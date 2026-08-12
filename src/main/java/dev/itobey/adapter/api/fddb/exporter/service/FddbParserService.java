package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FddbParserService {

    private static final String XPATH_AUTH_STATUS = "//div[@class='quicklinks']/a[contains(@class, 'v2hdlnk') and (text()='Anmelden' or text()='Login')]";
    private static final String XPATH_PRODUCT_TABLE = "//table[@class='myday-table-std']/tbody/tr";

    /**
     * Sugar and fibre are not part of the product table, they only appear in the nutrient summary
     * below it. That summary is found by its row label rather than by its position in the document:
     * fddb.info dropped a block from the page in August 2026, which shifted every absolute path and
     * broke the export for every single day. The labels are English because the diary is always
     * requested as {@code lang=en}.
     */
    private static final Pattern SUGAR_LABEL = Pattern.compile("thereof\\s+sugar", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIBRE_LABEL = Pattern.compile("dietary\\s+fib(re|er)", Pattern.CASE_INSENSITIVE);

    private static final int COLUMN_CALORIES = 2;
    private static final int COLUMN_FAT = 3;
    private static final int COLUMN_CARBS = 4;
    private static final int COLUMN_PROTEIN = 5;

    public FddbData parseDiary(String input) throws AuthenticationException, ParseException {
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

    public void checkAuthentication(Document doc) throws AuthenticationException {
        Elements authStatus = doc.selectXpath(XPATH_AUTH_STATUS);
        if (!authStatus.isEmpty()) {
            String errorMsg = "Login to FDDB not successful, please check credentials";
            log.error(errorMsg);
            throw new AuthenticationException(errorMsg);
        }
    }

    private List<Product> parseProducts(Document doc) {
        List<Product> products = new ArrayList<>();
        Elements rows = doc.selectXpath(XPATH_PRODUCT_TABLE);

        for (int i = 0; i < rows.size() - 1; i++) {
            Element row = rows.get(i);
            Elements columns = row.select("td");

            if (columns.size() <= 1 || isCategoryRow(columns)) {
                continue;
            }

            products.add(createProduct(columns));
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
            setProductNameAndAmount(product, productLink);
            product.setLink(productLink.attr("href"));
        }

        product.setCalories(extractNumber(columns, COLUMN_CALORIES, "calories"));
        product.setFat(extractNumber(columns, COLUMN_FAT, "fat"));
        product.setCarbs(extractNumber(columns, COLUMN_CARBS, "carbs"));
        product.setProtein(extractNumber(columns, COLUMN_PROTEIN, "protein"));

        return product;
    }

    private void setProductNameAndAmount(Product product, Element productLink) {
        String fullName = productLink.text();
        String[] parts = fullName.split(" ", 3);
        if (parts.length == 3) {
            product.setAmount(parts[0] + " " + parts[1]);
            product.setName(parts[2]);
        } else {
            product.setName(fullName);
        }
    }

    private void setDayTotals(FddbData fddbData, Document doc) {
        Elements lastRow = doc.selectXpath(XPATH_PRODUCT_TABLE + "[last()]/td");
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
        if (columns.size() <= index) {
            throw new ParseException("cannot read " + name + ": expected at least " + (index + 1)
                    + " columns but the row has " + columns.size()
                    + ", the fddb.info page layout has likely changed");
        }
        return extractNumber(cellValue(columns.get(index)), name);
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

    private void checkDataAvailable(Document doc) throws ParseException {
        Elements lastRow = doc.selectXpath(XPATH_PRODUCT_TABLE + "[last()]/td");
        if (lastRow.isEmpty()) {
            throw new ParseException("cannot parse input. it's likely there is no data available for the given day");
        }
    }
}
