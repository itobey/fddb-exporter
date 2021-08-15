package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * Parses the HTML response from FDDB.
 */
@Service
@Slf4j
public class HtmlParser {

    final static String CSS_SELECTOR_PREFIX = "#content > div.mainblock > div.fullsizeblock > div:nth-child(2) > div > table:nth-child(5) > tbody > ";
    final static String CSS_SELECTOR_KCAL = CSS_SELECTOR_PREFIX + "tr:nth-child(1) > td:nth-child(2)";
    final static String CSS_SELECTOR_FAT = CSS_SELECTOR_PREFIX + "tr:nth-child(2) > td:nth-child(2) > span";
    final static String CSS_SELECTOR_CARBS = CSS_SELECTOR_PREFIX + "tr:nth-child(3) > td:nth-child(2) > span";
    final static String CSS_SELECTOR_SUGAR = CSS_SELECTOR_PREFIX + "tr:nth-child(4) > td:nth-child(2) > span";
    final static String CSS_SELECTOR_PROTEIN = CSS_SELECTOR_PREFIX + "tr:nth-child(5) > td:nth-child(2) > span";
    final static String CSS_SELECTOR_FIBER = CSS_SELECTOR_PREFIX + "tr:nth-child(8) > td:nth-child(2)";


    /**
     * Retrieves the wanted data from the response.
     *
     * @param response the response from FDDB
     * @return @{@link FddbData} containing all wanted data
     */
    public FddbData getDataFromResponse(String response) {
        Document doc = Jsoup.parse(response);
        Elements kcal = doc.select(CSS_SELECTOR_KCAL);
        Elements fat = doc.select(CSS_SELECTOR_FAT);
        Elements carbs = doc.select(CSS_SELECTOR_CARBS);
        Elements sugar = doc.select(CSS_SELECTOR_SUGAR);
        Elements protein = doc.select(CSS_SELECTOR_PROTEIN);
        Elements fiber = doc.select(CSS_SELECTOR_FIBER);

        return FddbData.builder()
                .kcal(parseKcal(kcal))
                .fat(parseIntFromElement(fat))
                .carbs(parseIntFromElement(carbs))
                .sugar(parseIntFromElement(sugar))
                .protein(parseIntFromElement(protein))
                .fiber(parseIntFromElement(fiber))
                .build();
    }

    /**
     * Parse the element to get the kcal value.
     *
     * @param element the html element
     * @return the kcal value
     */
    private int parseKcal(Elements element) {
        String substring = StringUtils.substringBetween(element.html(), "(", ")");
        return Integer.parseInt(substring.replaceAll("[^0-9,]", ""));
    }

    /**
     * Parse the element to get the value of the metric as integer.
     *
     * @param element the html element
     * @return the value as int
     */
    private int parseIntFromElement(Elements element) {
        String removedNonNumeric = element.html().replaceAll("[^0-9,]", "");
        String floatAsString = removedNonNumeric.replace(",", ".");
        return (int) Float.parseFloat(floatAsString);
    }

}
