package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.EmptyDiaryException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FddbParserService {

    private static final String AUTH_LINKS = ".quicklinks a.v2hdlnk, .topbar-links a";
    private static final String PRODUCT_ROWS = "table.myday-table-std > tbody > tr";
    private static final Pattern PRODUCT_AMOUNT = Pattern.compile(
            "^(\\d+(?:[.,]\\d+|/\\d+)?\\s+\\S+|Teelöffel|Esslöffel)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Sugar and fibre are not part of the product table, they only appear in the nutrient summary
     * below it. That summary is found by its row label rather than by its position in the document:
     * fddb.info dropped a block from the page in August 2026, which shifted every absolute path and
     * broke the export for every single day. The labels are English because the diary is always
     * requested as {@code lang=en}.
     */
    private static final Pattern SUGAR_LABEL = Pattern.compile("thereof\\s+sugar", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIBRE_LABEL = Pattern.compile("dietary\\s+fib(re|er)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_ENERGY_SUMMARY = Pattern.compile(
            "^0(?:\\.0+)?\\s+kJ\\s+\\(0(?:\\.0+)?\\s+kcal\\)\\s+from\\s+.+$",
            Pattern.CASE_INSENSITIVE);

    private static final int COLUMN_CALORIES = 2;
    private static final int COLUMN_FAT = 3;
    private static final int COLUMN_CARBS = 4;
    private static final int COLUMN_PROTEIN = 5;

    /**
     * Parses one diary page into its products and daily totals.
     *
     * @param input the raw HTML of an fddb.info diary response
     * @return the parsed day, without a date
     * @throws AuthenticationException if the page is the logged-out one
     * @throws EmptyDiaryException     if the page shows the recognized empty diary summary
     * @throws ParseException          if diary structure is missing or malformed
     */
    public FddbData parseDiary(String input) {
        Document doc = Jsoup.parse(input, "UTF-8");
        checkAuthentication(doc);
        checkDataAvailable(doc);

        try {
            FddbData fddbData = new FddbData();
            List<Product> products = parseProducts(doc);
            fddbData.setProducts(products);

            setDayTotals(fddbData, doc);

            return fddbData;
        } catch (ParseException parseException) {
            throw parseException;
        } catch (RuntimeException runtimeException) {
            // never let an unchecked parser failure escape: the export catches ParseException per
            // day and reports that day as unsuccessful, anything else aborts the whole run with a
            // bare HTTP 500
            throw new ParseException("cannot parse the fddb.info page, its layout has likely changed",
                    runtimeException);
        }
    }

    /**
     * Whether a response came back as the logged-out page.
     * <p>
     * The non-throwing counterpart of {@link #checkAuthentication(Document)}, for the adapter's
     * retry-with-a-fresh-login path, where being logged out is a condition to handle rather than an error.
     *
     * @param html the raw HTML of an fddb.info response
     * @return true if the page shows the login link instead of the diary
     */
    public boolean isLoggedOut(String html) {
        return hasLoginLink(Jsoup.parse(html, "UTF-8"));
    }

    /**
     * @param doc the parsed response page
     * @throws AuthenticationException if the page shows the login link instead of the diary
     */
    public void checkAuthentication(Document doc) {
        if (hasLoginLink(doc)) {
            String errorMsg = "Login to FDDB not successful, please check credentials";
            log.error(errorMsg);
            throw new AuthenticationException(errorMsg);
        }
    }

    private boolean hasLoginLink(Document doc) {
        return doc.select(AUTH_LINKS).stream()
                .anyMatch(link -> link.text().equalsIgnoreCase("Login")
                        || link.text().equalsIgnoreCase("Anmelden"));
    }

    private List<Product> parseProducts(Document doc) {
        List<Product> products = new ArrayList<>();
        Elements rows = doc.select(PRODUCT_ROWS);

        for (int i = 0; i < rows.size() - 1; i++) {
            Element row = rows.get(i);
            Elements columns = row.select("td");

            if (columns.size() <= 1 || row.hasClass("myday-mealtotal") || isCategoryRow(columns)) {
                continue;
            }

            products.add(createProduct(columns));
        }

        if (products.isEmpty()) {
            throw new ParseException("cannot find product entries in the fddb.info diary table, "
                    + "its layout has likely changed");
        }
        return products;
    }

    private boolean isCategoryRow(Elements columns) {
        return columns.stream()
                .anyMatch(column -> {
                    Element span = column.selectFirst("span[style]");
                    return span != null && span.attr("style").replaceAll("\\s+", "").contains("color:#AAAAAA");
                });
    }

    private Product createProduct(Elements columns) {
        Product product = new Product();
        Element productLink = columns.get(0).selectFirst("a");

        if (productLink != null) {
            setProductNameAndAmount(product, productLink.text());
            product.setLink(productLink.attr("href"));
        } else {
            // Recipes and AI foods have no link; the time is a separate child span.
            Element productCell = columns.get(0).clone();
            productCell.select(".mydayshowtime").remove();
            setProductNameAndAmount(product, productCell.text());
        }

        product.setCalories(extractNumber(columns, COLUMN_CALORIES, "calories"));
        product.setFat(extractNumber(columns, COLUMN_FAT, "fat"));
        product.setCarbs(extractNumber(columns, COLUMN_CARBS, "carbs"));
        product.setProtein(extractNumber(columns, COLUMN_PROTEIN, "protein"));

        return product;
    }

    private void setProductNameAndAmount(Product product, String fullName) {
        Matcher amount = PRODUCT_AMOUNT.matcher(fullName);
        if (amount.matches()) {
            product.setAmount(amount.group(1));
            product.setName(amount.group(2));
        } else {
            product.setName(fullName);
        }
    }

    private void setDayTotals(FddbData fddbData, Document doc) {
        Elements lastRow = doc.select(PRODUCT_ROWS).last().select("td");
        fddbData.setTotalCalories(extractNumber(lastRow, COLUMN_CALORIES, "total calories"));
        fddbData.setTotalFat(extractNumber(lastRow, COLUMN_FAT, "total fat"));
        fddbData.setTotalCarbs(extractNumber(lastRow, COLUMN_CARBS, "total carbs"));
        fddbData.setTotalProtein(extractNumber(lastRow, COLUMN_PROTEIN, "total protein"));
        fddbData.setTotalSugar(extractNumber(findNutrientValue(doc, SUGAR_LABEL, "sugar"), "total sugar"));
        fddbData.setTotalFibre(extractNumber(findNutrientValue(doc, FIBRE_LABEL, "fibre"), "total fibre"));
    }

    /**
     * Reads a value from the nutrient summary table by the label in its first column.
     *
     * @param label the label of the wanted row, matched against the whole cell text
     * @param name  what is being looked for, for the error message
     * @return the text of the value cell next to the label
     * @throws ParseException if no row carries that label
     */
    private String findNutrientValue(Document doc, Pattern label, String name) {
        // the product table has a matching shape but holds no summary, and a product could well be
        // named "sugar" - excluding it keeps a diary entry from being read as a day total
        for (Element row : doc.select("table:not(.myday-table-std) tr")) {
            Elements columns = row.select("td");
            if (columns.size() >= 2 && label.matcher(columns.get(0).text().trim()).matches()) {
                return cellValue(columns.get(1));
            }
        }
        throw new ParseException("cannot find the " + name + " row in the nutrient summary, "
                + "the fddb.info page layout has likely changed");
    }

    private double extractNumber(Elements columns, int index, String name) {
        if (columns.isEmpty()) {
            throw new ParseException("cannot read " + name + ": the row has no columns");
        }
        Element table = columns.first().closest("table");
        Elements headers = table.select("tr").first().select("th");
        int expectedColumns = headers.stream()
                .mapToInt(header -> header.hasAttr("colspan") ? Integer.parseInt(header.attr("colspan")) : 1)
                .sum();
        if (expectedColumns != 0 && expectedColumns != columns.size()) {
            throw new ParseException("cannot read " + name + ": expected " + expectedColumns
                    + " columns but the row has " + columns.size()
                    + ", the fddb.info page layout has likely changed");
        }
        String label = switch (index) {
            case COLUMN_CALORIES -> "Calorific value";
            case COLUMN_FAT -> "Fat";
            case COLUMN_CARBS -> "Carbohydrates";
            case COLUMN_PROTEIN -> "Protein";
            default -> throw new ParseException("unknown nutrient column: " + name);
        };
        // Six cells can mean either legacy separate kJ/kcal cells or the redesigned table
        // with an extra Alcohol column. Resolve by headers, never by the row's cell count.
        int offset = 0;
        Element column = null;
        for (Element header : headers) {
            int span = header.hasAttr("colspan") ? Integer.parseInt(header.attr("colspan")) : 1;
            if (header.text().replace('\u00a0', ' ').trim().equalsIgnoreCase(label)) {
                column = columns.get(offset + (index == COLUMN_CALORIES ? span - 1 : 0));
                break;
            }
            offset += span;
        }
        if (column == null) {
            throw new ParseException("cannot find the " + label + " column for " + name
                    + ", the fddb.info page layout has likely changed");
        }
        Element kcal = column.selectFirst(".md-kcal");
        return extractNumber(kcal != null ? kcal.text() : cellValue(column), name);
    }

    /**
     * The value of a table cell, without anything fddb.info appends to it in a separate tag - the
     * carbs cells carry the bread units in a second span, and "246.2 g (20.5 BE)" is not a number.
     */
    private String cellValue(Element column) {
        Element valueTag = column.selectFirst("span, b");
        return valueTag != null ? valueTag.text() : column.text();
    }

    private double extractNumber(String text, String name) {
        String number = text.replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException numberFormatException) {
            throw new ParseException("cannot read " + name + " as a number from '" + text + "'",
                    numberFormatException);
        }
    }

    /**
     * @param doc the parsed response page
     * @throws EmptyDiaryException if the page matches FDDB's empty diary layout
     * @throws ParseException if the diary table is missing or malformed without a recognized empty state
     */
    private void checkDataAvailable(Document doc) {
        // Empty pages retain the zero-energy overview, but omit both diary and nutrient tables.
        // Missing rows alone are not enough evidence: a layout change must remain a format error.
        Element summary = doc.selectFirst(".fddb-myday .md-summary .fddb-myday-subline");
        if (doc.select("table.myday-table-std, table.fddb-myday-nutrients").isEmpty()
                && summary != null && EMPTY_ENERGY_SUMMARY.matcher(summary.text()).matches()) {
            throw new EmptyDiaryException();
        }
        Element lastRow = doc.select(PRODUCT_ROWS).last();
        if (lastRow == null || lastRow.select("td").isEmpty()) {
            throw new ParseException("cannot find diary rows in the fddb.info page, its layout has likely changed");
        }
    }
}
