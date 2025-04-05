package org.example;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class RepetitParser {
    static class TutorInfo {
        String name;
        int price;
        int age;
        String experience;
        boolean hasKeywords;
        String profileUrl;

        public TutorInfo(String name, int price, int age, String experience, boolean hasKeywords, String profileUrl) {
            this.name = name;
            this.price = price;
            this.age = age;
            this.experience = experience;
            this.hasKeywords = hasKeywords;
            this.profileUrl = profileUrl;
        }
    }

    public static void main(String[] args) throws IOException {
        List<TutorInfo> tutors = new ArrayList<>();

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
                    int age = 0;
                    if (ageBlock != null) {
                        String ageText = ageBlock.text().replaceAll("[^0-9]", "");
                        if (!ageText.isEmpty()) {
                            age = Integer.parseInt(ageText);
                            if (age < 26) continue;
                        }
                    }

                    Element nameElement = profileDoc.selectFirst("div.intro__name > h1[itemprop=name]");
                    String name = nameElement != null ? nameElement.text() : "-";

                    Element expElement = profileDoc.selectFirst("span.intro__experience-value");
                    String experience = expElement != null ? expElement.text() : "-";

                    // Ищем цену за онлайн занятия
                    Elements priceBlocks = profileDoc.select(".price");
                    for (Element price : priceBlocks) {
                        Element title = price.selectFirst(".price__title");
                        Element value = price.selectFirst(".price__value");

                        if (title != null && value != null && title.text().toLowerCase().contains("онлайн")) {
                            String priceText = value.text().replaceAll("[^0-9]", "");
                            if (!priceText.isEmpty()) {
                                int parsedPrice = Integer.parseInt(priceText);

                                tutors.add(new TutorInfo(
                                        name,
                                        parsedPrice,
                                        age,
                                        experience,
                                        true,
                                        profileUrl
                                ));
                            }
                        }
                    }
                } catch (Exception e) {
                    //TODO
                    System.out.println("Ошибка при обработке профиля: " + profileUrl);
                }
            }
        }

        for (TutorInfo tutor : tutors) {
            System.out.printf("Name: %s | Price: %d | Age: %d | Experience: %s | Key words: %s | URL: %s%n",
                    tutor.name, tutor.price, tutor.age, tutor.experience, tutor.hasKeywords ? "Yes" : "No", tutor.profileUrl);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter("tutors.csv"))) {
            writer.println("Name,Price,Age,Experience,HasKeyWords,ProfileURL");
            for (TutorInfo t : tutors) {
                writer.printf("%s,%d,%d,%s,%s,%s%n",
                        escapeCsv(t.name), t.price, t.age, escapeCsv(t.experience), t.hasKeywords ? "Yes" : "No", t.profileUrl);
            }
        }

        double average = tutors.stream().mapToInt(t -> t.price).average().orElse(0);
        //TODO
        System.out.println("Средняя цена за онлайн-занятие: " + (int) average + " рублей");
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}


