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

        // Перебираем несколько страниц, можно увеличить до нужного числа
        for (int page = 32; page <= 48; page++) {
            String url = "https://repetit.ru/repetitors/angliyskiy-yazyk/?page=" + page;
            Document doc = Jsoup.connect(url).get();
            Elements cards = doc.select("a.teacher-card__name-link");

            for (Element card : cards) {
                String profileUrl = "https://repetit.ru" + card.attr("href");
                try {
                    Document profileDoc = Jsoup.connect(profileUrl).get();

                    // Ищем цену за онлайн занятия
                    Elements priceBlocks = profileDoc.select("p.price");
                    for (Element block : priceBlocks) {
                        Element title = block.selectFirst("span.price__title");
                        Element value = block.selectFirst("span.price__value");

                        if (title != null && value != null && title.text().toLowerCase().contains("онлайн")) {
                            String priceText = value.text().replaceAll("[^0-9]", "");
                            if (!priceText.isEmpty()) {
                                prices.add(Integer.parseInt(priceText));
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка при обработке профиля: " + profileUrl);
                }
            }
        }

        if (!prices.isEmpty()) {
            double average = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
            System.out.println("Средняя цена за онлайн-занятие: " + (int) average + " рублей");
        } else {
            System.out.println("Цены не найдены.");
        }
    }
}


