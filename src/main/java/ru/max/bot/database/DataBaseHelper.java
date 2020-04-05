package ru.max.bot.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import ru.max.bot.holders.RatesHolder;
import ru.max.bot.rent.*;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * Database operations
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataBaseHelper {

    private final MongoTemplate mongoTemplate;


    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public <T> T getFirstValue(String collection, String field, Bson bson) {

        T result = null;

        Optional<Document> doc = Optional.ofNullable(this.mongoTemplate.getDb()
                .getCollection(collection).find(bson).first());

        if (doc.isPresent()) {
            result = (T) doc.get().get(field);
        }
        return result;
    }

    /**
     * Return first object from collection by filter
     *
     * @param collection
     * @param bson
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getFirstDocByFilter(String collection, Bson bson) {

        T result = null;

        Optional<T> doc = (Optional<T>) Optional.ofNullable(this.mongoTemplate.getDb()
                .getCollection(collection).find(bson).first());
        if (doc.isPresent()) {
            result = (T) doc.get();
        }
        return result;
    }

    public RatesHolder getRatesForRecalc(String chatId, ObjectMapper mapper) {


        try {
            Optional<Document> document = Optional.ofNullable(this.mongoTemplate.getDb()
                    .getCollection("rent_const")
                    .find(Filters.eq("id_chat", chatId)).first());

            if (document.isPresent()) {
                try {
                    PrimaryLightHolder plh = mapper.readValue(
                            (String) document.get().get("light"), PrimaryLightHolder.class);

                    PrimaryWaterHolder pwh = mapper.readValue(
                            (String) document.get().get("water"), PrimaryWaterHolder.class);
                    return new RatesHolder(plh, pwh);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Can't get rates month!", e);
        }
        return null;
    }

    /**
     * Return rent rates
     *
     * @return message with rates
     */
    public String getRates(String chatId, ObjectMapper mapper, boolean isRus) {

        StringBuilder sb = new StringBuilder();

        try {
            Optional<Document> document = Optional.ofNullable(this.mongoTemplate.getDb()
                    .getCollection("rent_const")
                    .find(Filters.eq("id_chat", chatId)).first());
            document.ifPresent(doc -> {

                sb.append(
                        (isRus) ? "<b>Сумма аренды:</b> "
                                : "<b>Rent amount:</b> ")
                        .append(doc.get("rent_amount"))
                        .append((isRus) ? " руб" : " rub")
                        .append((isRus) ? "\n\n<b>Электричество</b>"
                                : "\n\n<b>Light</b>");

                try {
                    PrimaryLightHolder plh = mapper.readValue(
                            (String) doc.get("light"), PrimaryLightHolder.class);

                    for (Entry<String, Double> entry : plh.getRates()
                            .entrySet()) {
                        sb.append("\n").append(entry.getKey()).append(": ")
                                .append(entry.getValue())
                                .append((isRus) ? " руб" : " rub");
                    }

                    sb.append((isRus) ? "\n\n<b>Вода</b>\n"
                            : "\n\n<b>Water</b>\n");

                    PrimaryWaterHolder pwh = mapper.readValue(
                            (String) doc.get("water"), PrimaryWaterHolder.class);

                    sb.append((isRus) ? "Горячая: " : "Hot: ")
                            .append(pwh.getHotWaterRate())
                            .append((isRus) ? " руб" : " rub")
                            .append((isRus) ? "\nХолодная: " : "\nCold: ")
                            .append(pwh.getColdWaterRate())
                            .append((isRus) ? " руб" : " rub")
                            .append((isRus) ? "\nВодоотвод: " : "\nOutfall: ")
                            .append(pwh.getOutfallRate())
                            .append((isRus) ? " руб" : " rub");

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            });
        } catch (Exception e) {
            log.error("Can't get rates month!", e);
        }
        return sb.toString();
    }

    /**
     * Return statistic for require month
     *
     * @param month - rent month
     * @return - String message
     */
    public String getStatByMonth(String month, String idChat, boolean isRus) {

        StringBuilder sb = new StringBuilder();

        try {
            Optional<Document> document = Optional.ofNullable(this.getFirstDocByFilter(
                    "rent_stat",
                    Filters.and(Filters.eq("month", month),
                            Filters.eq("id_chat", idChat))));
            if (document.isPresent()) {

                try {
                    RentMonthHolder rentHolder = objectMapper.readValue(
                            (String) document.get().get("stat"), RentMonthHolder.class);

                    return getStatisticByMonth(rentHolder, isRus);
                } catch (Exception e1) {
                    log.error(e1.getMessage(), e1);
                }

            }
        } catch (Exception e) {
            log.error("Can't get stat by month {}!", month,
                    e);
        }
        return sb.toString();
    }


    public String getStatisticByMonth(RentMonthHolder rentHolder, boolean isRus) {

        StringBuilder sb = new StringBuilder();

        LastIndicationsHolder lastData = rentHolder
                .getLastIndications();

        Map<String, Double> lightLast = lastData.getLight();

        sb.append((isRus) ? "<b>Автор:</b> " : "<b>Added by:</b> ")
                .append(rentHolder.getOwner())
                .append((isRus) ? "\n<b>Месяц:</b> "
                        : "\n<b>Month:</b> ")
                .append(rentHolder.getMonth())
                .append((isRus) ? "\n<b>Конечная стоимость аренды:</b> "
                        : "\n<b>Final amount:</b> ")
                .append(String.format("%.2f",
                        rentHolder.getTotalAmount()))
                .append((isRus) ? " руб" : " rub")
                .append((isRus) ? "\n\n<b>Электричество</b>\n"
                        : "\n\n<b>Light</b>\n");

        rentHolder
                .getLight()
                .entrySet()
                .forEach(
                        e -> {
                            sb.append("<b>").append(e.getKey()).append("</b>")
                                    .append((isRus) ? " - показание: "
                                            : " - indication: ")
                                    .append(String.format("%.2f",
                                            lightLast.get(e
                                                    .getKey())))
                                    .append((isRus) ? "; использовано: "
                                            : "; used: ")
                                    .append(String.format("%.2f", e
                                            .getValue().getUsed()))
                                    .append((isRus) ? "; стоимость: "
                                            : "; price: ")
                                    .append(String.format("%.2f", e
                                            .getValue().getPrice()))
                                    .append((isRus) ? " руб; тариф: "
                                            : " rub; rate: ")
                                    .append(String.format("%.2f", e
                                            .getValue().getRate()))
                                    .append((isRus) ? " руб"
                                            : " rub").append("\n");
                        });

        if (rentHolder.getLight().size() > 0) {
            Double totalLigDoublePrice = rentHolder.getLight().values().stream().map(Counter::getPrice).reduce(0.0, Double::sum);
            sb.append((isRus) ? "<b>Общая стоимость света:</b> " : "<b>Total price for light:</b> ").append((String.format("%.2f", totalLigDoublePrice))).append((isRus) ? " руб"
                    : " rub").append("\n");
        }


        int sizeColdWater = rentHolder.getColdWater().size();
        int sizeHotWater = rentHolder.getHotWater().size();

        if (sizeColdWater > 0) {
            sb.append((isRus) ? "\n<b>Холодная вода</b>"
                    : "\n<b>Cold water</b>");
            rentHolder
                    .getColdWater()
                    .entrySet()
                    .forEach(
                            e -> {

                                sb.append("\n");
                                Optional<String> alias = Optional
                                        .ofNullable(e.getValue()
                                                .getAlias());

                                Double lastIndication = null;
                                int size = lastData.getColdWater()
                                        .size();
                                if (size == 1) {
                                    lastIndication = lastData
                                            .getColdWater().get(1)
                                            .getPrimaryIndication();
                                } else {
                                    for (Entry<Integer, WaterHolder> entry : lastData
                                            .getColdWater()
                                            .entrySet()) {

                                        WaterHolder wh = entry
                                                .getValue();

                                        if (alias.get().equals(
                                                wh.getAlias())) {
                                            lastIndication = wh
                                                    .getPrimaryIndication();
                                        }
                                    }
                                }

                                if (alias.isPresent()) {
                                    sb.append(
                                            "<b>" + alias.get()
                                                    + "</b>")
                                            .append(" - ");

                                }

                                sb.append(
                                        (isRus) ? "показание: "
                                                : "indication: ")
                                        .append(String.format(
                                                "%.2f",
                                                lastIndication))
                                        .append((isRus) ? "; использовано: "
                                                : "; used: ")
                                        .append(String.format(
                                                "%.2f", e
                                                        .getValue()
                                                        .getUsed()))
                                        .append((isRus) ? "; стоимость: "
                                                : "; price: ")
                                        .append(String
                                                .format("%.2f", e
                                                        .getValue()
                                                        .getPrice()))
                                        .append((isRus) ? " руб; тариф: "
                                                : " rub; rate: ")
                                        .append(String.format(
                                                "%.2f", e
                                                        .getValue()
                                                        .getRate()))
                                        .append((isRus) ? " руб"
                                                : " rub");
                            });
        }

        if (sizeHotWater > 0) {
            sb.append((isRus) ? "\n\n<b>Горячая вода</b>"
                    : "\n\n<b>Hot water</b>");
            rentHolder
                    .getHotWater()
                    .entrySet()
                    .forEach(
                            e -> {
                                sb.append("\n");
                                Optional<String> alias = Optional
                                        .ofNullable(e.getValue()
                                                .getAlias());

                                if (alias.isPresent()) {
                                    sb.append(
                                            "<b>" + alias.get()
                                                    + "</b>")
                                            .append(" - ");
                                }

                                Double lastIndication = null;
                                int size = lastData.getHotWater()
                                        .size();
                                if (size == 1) {
                                    lastIndication = lastData
                                            .getHotWater().get(1)
                                            .getPrimaryIndication();
                                } else {
                                    for (Entry<Integer, WaterHolder> entry : lastData
                                            .getHotWater()
                                            .entrySet()) {

                                        WaterHolder wh = entry
                                                .getValue();

                                        if (alias.get().equals(
                                                wh.getAlias())) {
                                            lastIndication = wh
                                                    .getPrimaryIndication();
                                        }
                                    }
                                }
                                sb.append(
                                        (isRus) ? "показание: "
                                                : "indication: ")
                                        .append(String.format(
                                                "%.2f",
                                                lastIndication))
                                        .append((isRus) ? "; использовано: "
                                                : "; used: ")
                                        .append(String.format(
                                                "%.2f", e
                                                        .getValue()
                                                        .getUsed()))
                                        .append((isRus) ? "; стоимость: "
                                                : "; price: ")
                                        .append(String
                                                .format("%.2f", e
                                                        .getValue()
                                                        .getPrice()))
                                        .append((isRus) ? " руб; тариф: "
                                                : " rub; rate: ")
                                        .append(String.format(
                                                "%.2f", e
                                                        .getValue()
                                                        .getRate()))
                                        .append((isRus) ? " руб"
                                                : " rub");
                            });

        }

        Optional<Counter> outfall = Optional.ofNullable(rentHolder
                .getOutfall());

        outfall.ifPresent(counter -> sb.append(
                (isRus) ? "\n\n<b>Водоотвод</b> - количество: "
                        : "\n\n<b>Outfall</b> - count: ")
                .append(String.format("%.2f", counter
                        .getUsed()))
                .append((isRus) ? "; стоимость: " : "; price: ")
                .append(String.format("%.2f", counter
                        .getPrice()))
                .append((isRus) ? " руб; тариф: "
                        : " rub; rate: ")
                .append(String.format("%.2f", counter
                        .getRate()))
                .append((isRus) ? " руб" : " rub"));

        sb.append(
                (isRus) ? "\n\n<b>Стоимость аренды:</b> "
                        : "\n\n<b>Rent Amount:</b> ")
                .append(String.format("%.2f",
                        rentHolder.getRentAmount()))
                .append((isRus) ? " руб" : " rub");

        if (null != rentHolder.getTakeout()) {
            sb.append(
                    (isRus) ? "\n<b>Вычет:</b> "
                            : "\n<b>Takeout:</b> ")
                    .append(String.format("%.2f",
                            rentHolder.getTakeout()))
                    .append((isRus) ? " руб" : " rub")
                    .append(" - ")
                    .append(rentHolder.getTakeoutDesc());
        }

        return sb.toString();
    }

    /**
     * Get total amount for rent by months
     *
     * @return String message with history by months
     */
    public String getPaymentsHistory(String chatId, boolean isRus) {

        StringBuilder sb = new StringBuilder();

        try (MongoCursor<Document> iter = this.mongoTemplate.getDb().getCollection("rent_stat")
                .find(Filters.eq("id_chat", chatId))
                .sort(Sorts.descending("add_date", "-1")).iterator()) {

            sb.append((isRus) ? "История:\n" : "History:\n");
            while (iter.hasNext()) {
                Document document = iter.next();
                sb.append("\n")
                        .append(document.get("month"))
                        .append(": ")
                        .append(String
                                .format("%.2f",
                                        objectMapper.readValue(
                                                (String) document.get("stat"),
                                                RentMonthHolder.class)
                                                .getTotalAmount()))
                        .append((isRus) ? " руб" : " rub");
            }
        } catch (Exception e) {
            log.error("Can't get history!", e);
        }
        return sb.toString();
    }

    /**
     * Delete statistics by month
     *
     * @param idChat - unique chat id
     * @param month  - month for deleting
     * @return statuse execute true or false
     */
    public boolean deleteMothStat(String idChat, String month) {

        boolean status = true;
        boolean isLastRecord = false;

        //check exist the month
        Optional<Document> document = Optional.ofNullable(this.getFirstDocByFilter(
                "rent_stat",
                Filters.and(Filters.eq("month", month),
                        Filters.eq("id_chat", idChat))));

        if (document.isEmpty()) {
            log.debug("Asked month not found");
            return false;
        }

        // check count document -> if == 1 remove all (purge)
        if (getDocsCount("rent_stat", idChat) == 1) {
            status = purgeAll(idChat);
        } else {
            // check on last added record
            try {
                Optional<Document> lastRecord = Optional.ofNullable(this.mongoTemplate.getDb()
                        .getCollection("rent_stat")
                        .find(Filters.eq("id_chat", idChat)).limit(1)
                        .sort(Sorts.descending("add_date", "-1")).first());
                if (lastRecord.isPresent()) {
                    String lastMonthAdded = lastRecord.get().getString("month");

                    if (lastMonthAdded.toLowerCase().equalsIgnoreCase(month)) {
                        isLastRecord = true;
                    }
                }
            } catch (Exception e) {
                status = false;
                log.error(e.getMessage(), e);
            }

            try {
                DeleteResult dr = this.mongoTemplate.getDb().getCollection("rent_stat").deleteOne(
                        Filters.and(Filters.eq("id_chat", idChat),
                                Filters.eq("month", month)));
                if (dr.getDeletedCount() > 0) {
                    log.debug(
                            "Month {} deleted successfully! Row deleted {}",
                            month, dr.getDeletedCount());
                }

                if (isLastRecord) {
                    Document lastRecord = this.mongoTemplate.getDb().getCollection("rent_stat")
                            .find(Filters.eq("id_chat", idChat)).limit(1)
                            .sort(Sorts.descending("add_date", "-1")).first();

                    log.debug("Last record detected!");

                    try {
                        RentMonthHolder rent = objectMapper.readValue(
                                (String) lastRecord.get("stat"),
                                RentMonthHolder.class);

                        this.mongoTemplate.getDb().getCollection("rent_const")
                                .updateOne(
                                        this.mongoTemplate.getDb().getCollection("rent_const")
                                                .find(Filters.eq("id_chat",
                                                        idChat)).first(),
                                        new Document(
                                                "$set",
                                                new Document(
                                                        "last_indications",
                                                        objectMapper
                                                                .writeValueAsString(rent
                                                                        .getLastIndications()))));
                    } catch (Exception e) {
                        log.error(
                                "Can't update last indications!", e);
                    }

                }
            } catch (Exception e) {
                status = false;
                log.error("Can't purge statistics!", e);
            }
        }
        return status;
    }

    /**
     * Erase all information about rent
     *
     * @param chatId - chat it for which execute erase
     * @return
     */
    public boolean purgeAll(String chatId) {

        boolean status = true;

        try {
            this.mongoTemplate.getDb().getCollection("rent_const").deleteMany(
                    Filters.eq("id_chat", chatId));
            log.debug(
                    "Primary values of chat with id {} deleted successfully!",
                    chatId);
            DeleteResult dr = this.mongoTemplate.getDb().getCollection("rent_stat").deleteMany(
                    Filters.eq("id_chat", chatId));
            log.debug("Months deleted: {}", dr.getDeletedCount());
        } catch (Exception e) {
            status = false;
            log.error("Can't purge statistics! Error: {}", e.getMessage(), e);
        }
        return status;
    }

    /**
     * Update rates
     *
     * @param <T>
     * @param idChat - unique chat id
     * @param field  - field for update
     * @param value  - new value
     * @return status execute true or false
     */
    public <T> boolean updateField(String collection, String idChat,
                                   String field, T value) {

        boolean status = true;

        try {
            this.mongoTemplate.getDb().getCollection(collection).updateOne(
                    this.mongoTemplate.getDb().getCollection(collection)
                            .find(Filters.eq("id_chat", idChat)).first(),
                    new Document("$set", new Document(field, value)));
        } catch (Exception e) {
            status = false;
            log.error("Can't update {} at {}!", field, collection, e);
        }

        return status;
    }

    /**
     * Initialization primary indications like light,water and rates for it
     *
     * @param <T>
     * @param field - field in db
     * @return boolean status execute
     */
    public <T> boolean insertPrimaryCounters(T obj, String field,
                                             String idChat, String owner) {

        boolean status = true;
        boolean primarySet = false;

        try {
            Optional<Document> primaries = Optional.ofNullable(this.mongoTemplate.getDb()
                    .getCollection("rent_const")
                    .find(Filters.eq("id_chat", idChat)).first());
            if (primaries.isPresent()) {
                this.mongoTemplate.getDb().getCollection("rent_const").updateOne(
                        this.mongoTemplate.getDb().getCollection("rent_const")
                                .find(Filters.eq("id_chat", idChat)).first(),
                        new Document("$set", new Document(field, obj)));

                primarySet = primaries.get().get("light") != null
                        && primaries.get().get("rent_amount") != null && primaries
                        .get().get("water") != null;

            } else {
                this.mongoTemplate.getDb().getCollection("rent_const").insertOne(
                        new Document(field, obj).append("id_chat", idChat)
                                .append("owner", owner));

            }

            if (primarySet) {
                primaries = Optional.ofNullable(this.mongoTemplate.getDb()
                        .getCollection("rent_const")
                        .find(Filters.eq("id_chat", idChat)).first());

                LastIndicationsHolder lastIndications = new LastIndicationsHolder();
                try {
                    lastIndications.setLight(objectMapper.readValue(
                            (String) primaries.get().get("light"),
                            PrimaryLightHolder.class).getIndications());

                    lastIndications.setColdWater(objectMapper.readValue(
                            (String) primaries.get().get("water"),
                            PrimaryWaterHolder.class).getColdWater());
                    lastIndications.setHotWater(objectMapper.readValue(
                            (String) primaries.get().get("water"),
                            PrimaryWaterHolder.class).getHotWater());

                    this.mongoTemplate.getDb().getCollection("rent_const")
                            .updateOne(
                                    this.mongoTemplate.getCollection("rent_const")
                                            .find(Filters.eq("id_chat", idChat))
                                            .first(),
                                    new Document(
                                            "$set",
                                            new Document(
                                                    "last_indications",
                                                    objectMapper
                                                            .writeValueAsString(lastIndications))));

                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            status = false;
            log.error("Can't insert primary values for {}!", field, e);
        }
        return status;
    }

    /**
     * Save statistic of month
     *
     * @param total RentMonthHolder instance
     * @return boolean status execute
     */
    public boolean insertMonthStat(RentMonthHolder total) {

        boolean status = true;

        if (null != getFirstDocByFilter(
                "rent_stat",
                Filters.and(Filters.eq("month", total.getMonth()),
                        Filters.eq("id_chat", total.getChatId())))) {

            this.mongoTemplate.getDb().getCollection("rent_stat").deleteOne(
                    Filters.and(Filters.eq("id_chat", total.getChatId()),
                            Filters.eq("month", total.getMonth())));
            log.debug("Month exist! Month will be replace");
        }

        try {
            this.mongoTemplate.getDb().getCollection("rent_stat").insertOne(
                    new Document("month", total.getMonth())
                            .append("stat",
                                    objectMapper.writeValueAsString(total))
                            .append("id_chat", total.getChatId())
                            .append("who_set", total.getOwner())
                            .append("add_date", new Date().getTime()));
        } catch (Exception e) {
            status = false;
            log.error("Can't insert month stat!",
                    e);
        }

        // update last indications
        try {
            this.mongoTemplate.getDb().getCollection("rent_const").updateOne(
                    this.mongoTemplate.getDb().getCollection("rent_const")
                            .find(Filters.eq("id_chat", total.getChatId()))
                            .first(),
                    new Document("$set", new Document("last_indications",
                            objectMapper.writeValueAsString(total
                                    .getLastIndications()))));
        } catch (Exception e) {
            status = false;
            log.error("Can't update last indications!", e);
        }
        return status;
    }

    /**
     * Return telegram api token
     *
     * @return token for calling api
     */
    public Optional<String> getToken() {

        Optional<String> result = Optional.empty();

        try {
            result = Optional.ofNullable(this.mongoTemplate.getDb().getCollection("bot_data")
                    .find(Filters.eq("type", "telegram_api")).first()
                    .getString("token"));
        } catch (Exception e) {
            log.error("Can't get token!", e);
        }
        return result;

    }

    /**
     * Return paid months for the rent
     *
     * @param chatId - chat id
     * @return List<String> with paid months
     */
    public List<String> getPaidRentMonths(String chatId) {

        List<String> months = new LinkedList<>();

        try (MongoCursor<Document> iterator = this.mongoTemplate.getDb().getCollection("rent_stat")
                .find(Filters.eq("id_chat", chatId))
                .sort(Sorts.descending("add_date", "-1")).iterator()) {

            while (iterator.hasNext()) {
                Document doc = iterator.next();
                months.add(doc.getString("month"));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return months;
    }

    /**
     * Check exist rent user
     *
     * @param chatId - unique chat id
     * @return existing flag
     */
    public boolean existRentUser(String chatId) {

        Optional<Document> rentConsts = Optional.empty();

        try {
            rentConsts = Optional.ofNullable(this.mongoTemplate.getDb()
                    .getCollection("rent_const")
                    .find(Filters.eq("id_chat", chatId)).first());

            if (rentConsts.isPresent()) {
                boolean status = rentConsts.get().get("light") != null
                        && rentConsts.get().get("rent_amount") != null && rentConsts
                        .get().get("water") != null;

                if (status && rentConsts.get().get("last_indications") == null) {
                    LastIndicationsHolder lastIndications = new LastIndicationsHolder();
                    try {
                        lastIndications.setLight(objectMapper.readValue(
                                (String) rentConsts.get().get("light"),
                                PrimaryLightHolder.class).getIndications());

                        lastIndications.setColdWater(objectMapper.readValue(
                                (String) rentConsts.get().get("water"),
                                PrimaryWaterHolder.class).getColdWater());
                        lastIndications.setHotWater(objectMapper.readValue(
                                (String) rentConsts.get().get("water"),
                                PrimaryWaterHolder.class).getHotWater());

                        this.mongoTemplate.getDb().getCollection("rent_const")
                                .updateOne(
                                        this.mongoTemplate.getDb().getCollection("rent_const")
                                                .find(Filters.eq("id_chat",
                                                        chatId)).first(),
                                        new Document(
                                                "$set",
                                                new Document(
                                                        "last_indications",
                                                        objectMapper
                                                                .writeValueAsString(lastIndications))));

                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                return status;
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return false;
    }

    /**
     * Check exist rent statistic by user
     *
     * @param chatId - unique chat id
     * @return existing flag
     */
    public boolean existPayment(String chatId) {

        Optional<Document> rentStat = Optional.empty();

        try {
            rentStat = Optional.ofNullable(this.mongoTemplate.getDb().getCollection("rent_stat")
                    .find(Filters.eq("id_chat", chatId)).first());

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return rentStat.isPresent();
    }

    public long getDocsCount(String collection, String idChat) {
        return this.mongoTemplate.getDb().getCollection(collection).count(
                Filters.eq("id_chat", idChat));
    }
}
