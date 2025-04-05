package org.example;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RepetitParser {
    public static void main(String[] args) throws IOException {
        List<Integer> prices = new ArrayList<>();
        List<String> keywords = List.of("говор", "общен", "speaking", "живой", "устный", "барьер");

        // Перебираем несколько страниц, можно увеличить до нужного числа
        for (int page = 10; page <= 12; page++) {
            String url = "https://repetit.ru/repetitors/angliyskiy-yazyk/?page=" + page;
            Document doc = Jsoup.connect(url).get();
            Elements cards = doc.select("a.teacher-card__name-link");

            for (Element card : cards) {
                String profileUrl = "https://repetit.ru" + card.attr("href");
                try {
                    Document profileDoc = Jsoup.connect(profileUrl).get();

                    boolean hasOnline = profileDoc.select(".price__title").stream()
                            .anyMatch(e -> e.text().toLowerCase().contains("онлайн"));
                    if (!hasOnline) continue;

                    Element descBlock = profileDoc.selectFirst("#description-paragraph");
                    if (descBlock == null) continue;

                    String desc = descBlock.text().toLowerCase();
                    boolean containsKeyword = keywords.stream().anyMatch(desc::contains);
                    if (!containsKeyword) continue;

                    Element ageBlock = profileDoc.selectFirst(".intro__age-value");
                    if (ageBlock != null) {
                        String ageText = ageBlock.text().replaceAll("[^0-9]", "");
                        if (!ageText.isEmpty() && Integer.parseInt(ageText) < 26) continue;
                    }

                    // Ищем цену за онлайн занятия
                    Elements priceBlocks = profileDoc.select(".price");
                    for (Element price : priceBlocks) {
                        Element title = price.selectFirst(".price__title");
                        Element value = price.selectFirst(".price__value");

                        if (title != null && value != null && title.text().toLowerCase().contains("онлайн")) {
                            String priceText = value.text().replaceAll("[^0-9]", "");
                            if (!priceText.isEmpty()) {
                                prices.add(Integer.parseInt(priceText));
                            }
                        }
                    }
                } catch (Exception e) {
                    //TODO
                    System.out.println("Ошибка при обработке профиля: " + profileUrl);
                }
            }
        }

        if (!prices.isEmpty()) {
            double average = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
            //TODO
            System.out.println("Средняя цена за онлайн-занятие: " + (int) average + " рублей");
        } else {
            //TODO
            System.out.println("Цены не найдены.");
        }
    }
}


