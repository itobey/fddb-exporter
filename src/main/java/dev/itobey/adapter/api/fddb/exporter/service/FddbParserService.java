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

@Service
@Slf4j
public class FddbParserService {

    private static final String XPATH_AUTH_STATUS = "//div[@class='quicklinks']/a[contains(@class, 'v2hdlnk') and (text()='Anmelden' or text()='Login')]";
    private static final String XPATH_PRODUCT_TABLE = "//table[@class='myday-table-std']/tbody/tr";
    private static final String XPATH_SUGAR = "//*[@id=\"content\"]/div[3]/div[2]/div[2]/div/table[2]/tbody/tr[4]/td[2]/span";
    private static final String XPATH_FIBER = "//*[@id=\"content\"]/div[3]/div[2]/div[2]/div/table[2]/tbody/tr[8]/td[2]/b";

    public FddbData parseDiary(String input) throws AuthenticationException, ParseException {
        Document doc = Jsoup.parse(input, "UTF-8");
        checkAuthentication(doc);
        checkDataAvailable(doc);

        FddbData fddbData = new FddbData();
        List<Product> products = parseProducts(doc);
        fddbData.setProducts(products);

        setDayTotals(fddbData, doc);

        return fddbData;
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
        Elements lastRow = doc.selectXpath(XPATH_PRODUCT_TABLE + "[last()]/td");
        fddbData.setTotalCalories(extractNumber(lastRow.get(2).text()));
        fddbData.setTotalFat(extractNumber(lastRow.get(3).text()));
        fddbData.setTotalCarbs(extractNumber(lastRow.get(4).text()));
        fddbData.setTotalProtein(extractNumber(lastRow.get(5).text()));
        fddbData.setTotalSugar(extractNumber(doc.selectXpath(XPATH_SUGAR).text()));
        fddbData.setTotalSugar(extractNumber(doc.selectXpath(XPATH_SUGAR).text()));
        fddbData.setTotalFibre(extractNumber(doc.selectXpath(XPATH_FIBER).text()));
    }

    private double extractNumber(String text) {
        return Double.parseDouble(text.replaceAll("[^0-9.]", ""));
    }

    private void checkDataAvailable(Document doc) throws ParseException {
        Elements lastRow = doc.selectXpath(XPATH_PRODUCT_TABLE + "[last()]/td");
        if (lastRow.isEmpty()) {
            throw new ParseException("cannot parse input. it's likely there is no data available for the given day");
        }
    }
}