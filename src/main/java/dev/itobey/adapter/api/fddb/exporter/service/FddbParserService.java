package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class FddbParserService {

    private static final String CSS_SELECTOR_AUTH_STATUS = "#fddb-headerwrapper > div.quicklinks > a:nth-child(5)";
    private static final String CSS_SELECTOR_PRODUCT_TABLE = "table.myday-table-std tr";
    public static final String CSS_SELECTOR_PREFIX = "#content > div.mainblock > div.fullsizeblock > div:nth-child(2) > div > table:nth-child(5) > tbody > ";
    public static final String CSS_SELECTOR_SUGAR = CSS_SELECTOR_PREFIX + "tr:nth-child(4) > td:nth-child(2) > span";
    public static final String CSS_SELECTOR_FIBER = CSS_SELECTOR_PREFIX + "tr:nth-child(8) > td:nth-child(2)";

    public FddbData parseDiary(String input) throws AuthenticationException {
        Document doc = Jsoup.parse(input, "UTF-8");
        checkAuthentication(doc);

        FddbData fddbData = new FddbData();
        List<Product> products = parseProducts(doc);
        fddbData.setProducts(products);

        setDayTotals(fddbData, doc);

        return fddbData;
    }

    private void checkAuthentication(Document doc) throws AuthenticationException {
        Elements authStatus = doc.select(CSS_SELECTOR_AUTH_STATUS);
        if ("Anmelden".equals(authStatus.html()) || "Login".equals(authStatus.html())) {
            log.error("Error - not logged into FDDB");
            throw new AuthenticationException("Not logged into FDDB");
        }
    }

    private List<Product> parseProducts(Document doc) {
        List<Product> products = new ArrayList<>();
        Elements rows = doc.select(CSS_SELECTOR_PRODUCT_TABLE);

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

        product.setCalories(extractNumber(columns.get(2).text()));
        product.setFat(extractNumber(columns.get(3).text()));
        product.setCarbs(extractNumber(columns.get(4).text()));
        product.setProtein(extractNumber(columns.get(5).text()));

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
        Elements lastRow = Objects.requireNonNull(doc.select(CSS_SELECTOR_PRODUCT_TABLE).last()).select("td");
        fddbData.setTotalCalories(extractNumber(lastRow.get(2).text()));
        fddbData.setTotalFat(extractNumber(lastRow.get(3).text()));
        fddbData.setTotalCarbs(extractNumber(lastRow.get(4).text()));
        fddbData.setTotalProtein(extractNumber(lastRow.get(5).text()));
        fddbData.setTotalSugar(extractNumber(doc.select(CSS_SELECTOR_SUGAR).html()));
        fddbData.setTotalFibre(extractNumber(doc.select(CSS_SELECTOR_FIBER).html()));
    }

    private double extractNumber(String text) {
        return Double.parseDouble(text.replaceAll("[^0-9.]", ""));
    }
}