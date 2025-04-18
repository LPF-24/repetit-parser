package org.example;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import java.util.concurrent.*;

public class RepetitParser {
    static class TutorInfo {
        String name;
        int price;
        int age;
        String experience;
        boolean hasKeywords;
        int reviews;
        String profileUrl;

        public TutorInfo(String name, int price, int age, String experience, boolean hasKeywords, int reviews,
                         String profileUrl) {
            this.name = name;
            this.price = price;
            this.age = age;
            this.experience = experience;
            this.hasKeywords = hasKeywords;
            this.reviews = reviews;
            this.profileUrl = profileUrl;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(RepetitParser.class);
    private static final List<String> KEYWORDS = List.of("говор", "общен", "speaking", "живой", "устный", "барьер");
    private static final int THREADS = 6; // Можно увеличить, но лучше не более 10 для бережной нагрузки
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    private static int minPrice;
    private static int maxPrice;
    private static int minAge;
    private static int maxAge;

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Minimum price (in rubles): ");
        minPrice = scanner.nextInt();
        System.out.println("Maximum price (in rubles): ");
        maxPrice = scanner.nextInt();
        System.out.println("Minimum age: ");
        minAge = scanner.nextInt();
        System.out.println("Maximum age: ");
        maxAge = scanner.nextInt();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<TutorInfo>> futures = new ArrayList<>();
        long before = System.currentTimeMillis();

        for (int page = 1; page <= 1225; page++) {
            String url = "https://repetit.ru/repetitors/angliyskiy-yazyk/?page=" + page;
            Document doc = safeConnect(url);
            Elements cards = doc.select("a.teacher-card__name-link");

            for (Element card : cards) {
                String profileUrl = "https://repetit.ru" + card.attr("href");
                futures.add(executor.submit(() -> processProfile(profileUrl)));
            }
        }

        executor.shutdown();
        if (!executor.awaitTermination(90, TimeUnit.MINUTES)) {
            logger.warn("Waiting time expired - not all tasks were completed");
        }

        List<TutorInfo> tutors = new ArrayList<>();
        for (Future<TutorInfo> future : futures) {
            try {
                TutorInfo tutor = future.get();
                if (tutor != null) {
                    tutors.add(tutor);
                }
            } catch (Exception e) {
                logger.error("Error while getting result: {}", e.getMessage());
            }
        }

        // Вывод результатов
        for (TutorInfo tutor : tutors) {
            logger.info("Name: {} | Price: {} | Age: {} | Experience: {} | Key words: {} | Reviews: {} | URL: {}",
                    tutor.name, tutor.price, tutor.age, tutor.experience,
                    tutor.hasKeywords ? "Yes" : "No", tutor.reviews, tutor.profileUrl);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter("tutors.csv"))) {
            writer.println("Name,Price,Age,Experience,HasKeyWords,Reviews,ProfileURL");
            for (TutorInfo t : tutors) {
                writer.printf("%s,%d,%d,%s,%s,%d,%s%n",
                        escapeCsv(t.name), t.price, t.age, escapeCsv(t.experience),
                        t.hasKeywords ? "Yes" : "No", t.reviews, t.profileUrl);
            }
        }

        double average = tutors.stream().mapToInt(t -> t.price).average().orElse(0);
        logger.info("Average price for an online lesson (in rubles): {} rubles", (int) average);
        long after = System.currentTimeMillis();
        logger.info("Time spent on processing (in minutes): {}", (after - before) / 60000);
    }

    private static TutorInfo processProfile(String profileUrl) {
        try {
            Document profileDoc = Jsoup.connect(profileUrl).userAgent(USER_AGENT).get();

            boolean hasOnline = profileDoc.select(".price__title").stream()
                    .anyMatch(e -> e.text().toLowerCase().contains("онлайн"));
            if (!hasOnline) return null;

            Element descBlock = profileDoc.selectFirst("#description-paragraph");
            if (descBlock == null) return null;

            String desc = descBlock.text().toLowerCase();
            boolean containsKeyword = KEYWORDS.stream().anyMatch(desc::contains);
            if (!containsKeyword) return null;

            Element ageBlock = profileDoc.selectFirst(".intro__age-value");
            int age = 0;
            if (ageBlock != null) {
                String ageText = ageBlock.text().replaceAll("[^0-9]", "");
                if (!ageText.isEmpty()) {
                    age = Integer.parseInt(ageText);
                    if (age < minAge || age > maxAge) return null;
                }
            }

            Element reviewsElement = profileDoc.selectFirst(".intro__reviews-count");
            int reviews = 0;
            if (reviewsElement != null) {
                String reviewsText = reviewsElement.text().replaceAll("[^0-9]", "");
                if (!reviewsText.isEmpty()) {
                    reviews = Integer.parseInt(reviewsText);
                    if (reviews < 10) return null;
                }
            }

            Element nameElement = profileDoc.selectFirst("div.intro__name > h1[itemprop=name]");
            String name = nameElement != null ? nameElement.text() : "-";

            Element expElement = profileDoc.selectFirst("span.intro__experience-value");
            String experience = expElement != null ? expElement.text() : "-";

            Elements priceBlocks = profileDoc.select(".price");
            Optional<Integer> onlinePrice = priceBlocks.stream()
                    .map(block -> {
                        Element title = block.selectFirst(".price__title");
                        Element value = block.selectFirst(".price__value");
                        if (title != null && value != null && title.text().toLowerCase().contains("онлайн")) {
                            String priceText = value.text().replaceAll("[^0-9]", "");
                            if (!priceText.isEmpty()) {
                                return Integer.parseInt(priceText);
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .filter(price -> price >= minPrice && price <= maxPrice)
                    .findFirst();

            if (onlinePrice.isEmpty()) return null;
            if (onlinePrice.get() > maxPrice) return null; // Пропускаем дорогих

            return new TutorInfo(name, onlinePrice.get(), age, experience, true, reviews, profileUrl);

        } catch (Exception e) {
            logger.error("Error processing profile: {}", profileUrl);
            return null;
        }
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static Document safeConnect(String url) throws IOException {
        int attempts = 0;
        while (attempts < 3) {
            try {
                return Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(10000)
                        .get();
            } catch (IOException e) {
                attempts++;
                if (attempts >= 3) throw e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }
        throw new IOException("Unreachable code");
    }
}



