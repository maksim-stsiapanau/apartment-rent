package ru.max.bot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.joda.time.DateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.max.bot.database.DataBaseHelper;
import ru.max.bot.holders.CommandHolder;
import ru.max.bot.holders.RatesHolder;
import ru.max.bot.holders.Response;
import ru.max.bot.holders.ResponseHolder;
import ru.max.bot.model.*;
import ru.max.bot.rent.*;
import ru.max.bot.rent.PrimaryLightHolder.Periods;
import ru.max.bot.rent.PrimaryLightHolder.PeriodsRus;
import ru.max.bot.telegram_api.KeyboardButton;
import ru.max.bot.telegram_api.ReplyKeyboardHide;
import ru.max.bot.telegram_api.ReplyKeyboardMarkup;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

import static ru.max.bot.tools.BotHelper.*;

/**
 * Messages checker
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessagesChecker {


    private final String telegramApiUrl;
    private final ObjectMapper objectMapper;
    private final DataBaseHelper dataBaseHelper;

    @Scheduled(fixedRate = 1000)
    public void task() {

        callApiGet("getUpdates", this.telegramApiUrl)
                .ifPresent(
                        inputData -> {
                            Optional<IncomeMessage> message = Optional.empty();

                            try {
                                message = Optional.ofNullable(objectMapper
                                        .readValue(inputData,
                                                IncomeMessage.class));
                            } catch (Exception e1) {
                                log.error(e1.getMessage(), e1);
                            }

                            boolean status = (message.isPresent()) ? message
                                    .get().getOk() : false;

                            if (status) {
                                Response response = parseMessage(message.get());

                                response.getResponses()
                                        .forEach(
                                                e -> {

                                                    try {
                                                        StringBuilder sb = new StringBuilder();

                                                        sb.append(
                                                                "sendMessage?chat_id=")
                                                                .append(e.getChatId())
                                                                .append("&parse_mode=HTML&text=")
                                                                .append(URLEncoder
                                                                        .encode(e.getResponseMessage(),
                                                                                StandardCharsets.UTF_8));
                                                        if (e.isNeedReplyMarkup()) {
                                                            sb.append(
                                                                    "&reply_markup=")
                                                                    .append(e.getReplyMarkup());
                                                        }

                                                        callApiGet(sb.toString(),
                                                                this.telegramApiUrl);
                                                    } catch (Exception e1) {
                                                        log.error(e1.getMessage(), e1);
                                                    }

                                                });

                                if (null != response.getMaxUpdateId()) {
                                    callApiGet(
                                            "getUpdates?offset="
                                                    + (response
                                                    .getMaxUpdateId() + 1)
                                                    + "", this.telegramApiUrl);
                                }

                            }
                        });

    }

    /**
     * @param message -
     * @return
     */
    private Response parseMessage(IncomeMessage message) {

        List<Result> result = message.getResult();
        Response resp = new Response();
        Integer updateId = null;
        String id = null;
        String owner = "";

        if (result.size() > 0) {
            for (Result res : result) {
                ResponseHolder rh = new ResponseHolder();

                boolean isRus = false;
                try {
                    updateId = res.getUpdateId();
                    Optional<Message> messageObj = Optional.ofNullable(res
                            .getMessage());

                    if (messageObj.isPresent()) {
                        String typeCommand = new String("simple_message");
                        List<Entity> entiies = messageObj.get().getEntities();

                        if (entiies.size() > 0) {
                            typeCommand = entiies.get(0).getType();
                        }
                        Chat chat = messageObj.get().getChat();
                        id = String.valueOf(chat.getId());
                        rh.setChatId(id);

                        owner = chat.getFirstName() + " " + chat.getLastName();

                        log.debug("ChatId: {}; Owner: {}", id, owner);

                        String text = messageObj.get().getText();

                        if (null == text) {
                            text = "";
                        }
                        if (text.contains(getEmoji("E29C85"))) {
                            text = text.replace(getEmoji("E29C85"), "");
                        }

                        text = text.trim().toLowerCase();

                        if (text.contains(",")) {
                            // need for double value
                            text = text.replace(",", ".");
                        }

                        boolean isMapper = false;
                        isRus = commandMapperRus.containsKey(text);

                        if (commandMapper.containsKey(text)
                                || commandMapperRus.containsKey(text)) {
                            isMapper = true;
                        }

                        String answer = (isRus) ? "Я не могу ответить на этот вопрос"
                                : "I can't answer at this question";

                        if ((typeCommand.equalsIgnoreCase("bot_command") || isMapper)) {

                            // check russian language
                            if (isMapper) {
                                if (isRus) {
                                    text = commandMapperRus.get(text);
                                } else {
                                    text = commandMapper.get(text);
                                }
                            }

                            // remove old command and object
                            activeCommand.remove(id);
                            chatObjectMapper.remove(id);

                            // set active command
                            activeCommand.put(id,
                                    new CommandHolder(text, isRus));

                            switch (text) {
                                case "/start": {
                                    List<List<String>> buttons = new ArrayList<>();
                                    buttons.add(getButtonsList("rent", "аренда"));

                                    answer = new StringBuilder()
                                            .append("For english tap rent:\n")
                                            .append("<b>rent</b> (/rent) - calculating rent\n\n")
                                            .append("Для продолжения на русском языке используйте команду:\n\n")
                                            .append("<b>аренда</b> - расчет арендной платы за месяц\n")
                                            .toString();

                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));
                                }
                                break;
                                case "/rent": {
                                    monthForEdit.remove(id);
                                    answer = getRentMenu(id, isRus, rh);
                                }
                                break;
                                case "/change_rent": {
                                    if (null != monthForEdit.get(id)) {
                                        try {
                                            Optional<Document> document = Optional.ofNullable(dataBaseHelper.getFirstDocByFilter(
                                                    "rent_stat",
                                                    Filters.and(Filters.eq("month", monthForEdit.get(id)),
                                                            Filters.eq("id_chat", id))));
                                            StringBuilder sb = new StringBuilder();
                                            if (document.isPresent()) {

                                                try {
                                                    RentMonthHolder rentHolder = objectMapper.readValue(
                                                            (String) document.get().get("stat"), RentMonthHolder.class);

                                                    sb.append(dataBaseHelper.getStatisticByMonth(rentHolder, isRus)).append("\n\n\n");
                                                    Double totalBefore = rentHolder.getTotalAmount();

                                                    setDefaultRentButtons(rh, isRus);
                                                    RatesHolder rates = dataBaseHelper.getRatesForRecalc(id, objectMapper);
                                                    if (null != rates) {
                                                        rates.getLight().getRates().forEach((key, value) -> {
                                                            Counter c = rentHolder.getLight().get(key).recalcCounter(value);
                                                            rentHolder.getLight().put(key, c);
                                                        });

                                                        rentHolder.getColdWater().forEach((key, prevCounter) -> {
                                                            if (prevCounter.getUsed() < 0) {
                                                                prevCounter.setUsed(rentHolder.getLastIndications()
                                                                        .getColdWater().get(1).getPrimaryIndication());
                                                            }
                                                            Counter recalcCounter = prevCounter.recalcCounter(rates.getWater()
                                                                    .getColdWaterRate());
                                                            rentHolder.getColdWater().put(key, recalcCounter);
                                                        });

                                                        rentHolder.getHotWater().forEach((key, prevCounter) -> {
                                                            if (prevCounter.getUsed() < 0) {
                                                                prevCounter.setUsed(rentHolder.getLastIndications()
                                                                        .getHotWater().get(1).getPrimaryIndication());
                                                            }
                                                            Counter recalcCounter = prevCounter.recalcCounter(rates.getWater()
                                                                    .getHotWaterRate());
                                                            rentHolder.getHotWater().put(key, recalcCounter);
                                                        });


                                                        Counter prevOutfall = rentHolder.getOutfall();

                                                        if (prevOutfall.getUsed() < 0) {
                                                            Double outfallCount = 0.0;
                                                            for (Entry<Integer, Counter> entry : rentHolder.getColdWater()
                                                                    .entrySet()) {
                                                                outfallCount += entry.getValue().getUsed();
                                                            }

                                                            for (Entry<Integer, Counter> entry : rentHolder.getHotWater()
                                                                    .entrySet()) {
                                                                outfallCount += entry.getValue().getUsed();
                                                            }
                                                            prevOutfall.setUsed(outfallCount);
                                                        }
                                                        Counter recalcOutfall = prevOutfall.recalcCounter(rates.getWater()
                                                                .getOutfallRate());
                                                        rentHolder.setOutfall(recalcOutfall);

                                                        sb.append("======================").append(dataBaseHelper
                                                                .getStatisticByMonth(rentHolder, isRus)).append("\n\n");

                                                        Double totalAfter = rentHolder.getTotalAmount();

                                                        double diff = totalBefore - totalAfter;
                                                        if (diff < 0) {
                                                            diff = totalAfter;
                                                        }
                                                        sb.append((isRus) ? "<b>Разница:</b> " : "<b>Difference:</b> ")
                                                                .append(String.format("%.2f", diff)).append((isRus) ? " руб" : " rub");

                                                    }
                                                    answer = sb.toString();
                                                    //get rates and and apply to rentHolder
                                                } catch (Exception e) {
                                                    log.error(e.getMessage(), e);
                                                }
                                            }
                                        } catch (Exception e) {
                                            log.error(e.getMessage(), e);
                                        }
                                    } else {
                                        answer = (isRus) ? "Месяц на редактирование не найден!"
                                                : "Month for edit not found!";
                                    }
                                }
                                break;
                                case "/rent_add": {
                                    RentHolder rent = new RentHolder(objectMapper, dataBaseHelper, id, owner);
                                    rent.initIndications();
                                    rentData.put(id, Optional.of(rent));

                                    defaultAddMonthButtons(rent, rh, isRus);


                                    answer = (isRus) ? "Для добавления нового месяца Вы можете использовать следующие команды:\n\n" +
                                            "<b>месяц</b> - название месяца\n" +
                                            "<b>показания света</b> - задать показания счетчика света\n" +
                                            "<b>показания воды</b> - задать показания счетчиков воды, водоотвод расчитывается автоматически\n" +
                                            "<b>вычет</b> - другие расходы (вычитаюся из конечной суммы аренды)\n" +
                                            "<b>рассчитать</b> - конечная стоимость аренды\n" +
                                            "<b>инфо</b> - проверить введенную информацию\n"
                                            : "I'm ready for set indications.\nYou can use next commands (Use buttons below):\n\n" +
                                            "name of month (/setmonth) - set the name of the rental month\n" +
                                            "light (/setlight) - set indications for light\n" +
                                            "water (/setwater) - set indications for water, outfall calculating automatically\n" +
                                            "takeout (/settakeout) - set takeout from rent\n" +
                                            "calc (/calc) - return total amount for month\n" +
                                            "current statistic (/getstat)- return rent statistics for adding month\n";
                                }
                                break;
                                case "/getrates": {
                                    answer = dataBaseHelper.getRates(
                                            id, objectMapper, isRus);
                                    if (answer.length() == 0) {
                                        answer = (isRus) ? "Тарифы не заданы"
                                                : "Rates are empty!";
                                    }
                                }
                                break;
                                case "/changerates": {
                                    List<List<String>> buttons = new ArrayList<>();
                                    if (isRus) {
                                        buttons.add(getButtonsList("горячая вода",
                                                "холодная вода"));
                                        buttons.add(getButtonsList("водоотвод",
                                                "электричество"));
                                        buttons.add(getButtonsList("сумма аренды",
                                                "главное меню"));

                                        answer = "Вы можете использовать следующие команды:\n\n" +
                                                "<b>горячая вода</b> - изменить тариф на горячую воду\n" +
                                                "<b>холодная вода</b> - изменить тариф на холодную воду\n" +
                                                "<b>водоотвод</b> - изменить тариф на водоотвод\n" +
                                                "<b>электричество</b> - изменить тариф на электроэнергию\n" +
                                                "<b>сумма аренды</b> - изменить сумму аренды";
                                    } else {
                                        buttons.add(getButtonsList("hot water",
                                                "cold water"));
                                        buttons.add(getButtonsList("outfall",
                                                "light rate"));
                                        buttons.add(getButtonsList("rent amount",
                                                "main menu"));

                                        answer = "You can change next rates via follows commands (Use buttons below):\n\n" +
                                                "hot water (/changehwrateset) - set new hot water rate\n" +
                                                "cold water (/changecwrate) - set new cold water rate\n" +
                                                "outfall (/changeoutfallrate) - set new outfall rate\n" +
                                                "light rate (/changelightrate) - set new light rate\n" +
                                                "rent amount (/changera) - set new rent amount";
                                    }

                                    cacheButtons.put(id, buttons);

                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));

                                }
                                break;
                                case "/changehwrate": {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Для изменения тарифа горячей воды отправьте новое значение"
                                            : "For change hot water rate send simple message with new hot water rate";
                                }
                                break;
                                case "/changecwrate": {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Для изменения тарифа холодной воды отправьте новое значение"
                                            : "For change cold water rate send simple message with new cold water rate";
                                }
                                break;
                                case "/changelightrate": {
                                    PrimaryLightHolder light = objectMapper
                                            .readValue(
                                                    (String) dataBaseHelper
                                                            .getFirstValue(
                                                                    "rent_const",
                                                                    "light",
                                                                    Filters.eq(
                                                                            "id_chat",
                                                                            id)),
                                                    PrimaryLightHolder.class);

                                    Map<String, Double> lightRates = light
                                            .getRates();

                                    int sizeRates = lightRates.size();

                                    switch (sizeRates) {
                                        case 1:
                                            answer = (isRus) ? "У вас однотарифный счетчик. Задайте новое значение тарифа"
                                                    : "You have one-tariff counter. Please set new value for it";
                                            break;
                                        case 2:
                                            answer = (isRus) ? "У вас двухтарифный счетчик. Выберите нужный тариф для обновления"
                                                    : "You have two-tariff counter. Use buttons below for update of needed tariff";
                                            break;
                                        case 3:
                                            answer = (isRus) ? "У вас трехтарифный счетчик. Выберите нужный тариф для обновления"
                                                    : "You have three-tariff counter. Use buttons below for update of needed tariff";
                                            break;
                                        default:
                                            break;
                                    }

                                    if (sizeRates > 1) {
                                        List<List<String>> buttons = new LinkedList<>();
                                        List<String> names = new LinkedList<>();
                                        buttons.add(names);

                                        lightRates.forEach((key, value) -> names.add(key));

                                        rh.setNeedReplyMarkup(true);
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));
                                    }

                                }
                                break;
                                case "/changeoutfallrate": {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Для изменения тарифа водоотвода отправьте новое значение"
                                            : "For change outfall rate send simple message with new outfall rate";
                                }
                                break;
                                case "/changera": {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Для изменения суммы аренды отправьте новое значение"
                                            : "For change rent amount send simple message with new rent amount";
                                }
                                break;
                                case "/delmonthstat": {
                                    List<List<String>> buttons = new ArrayList<>();

                                    dataBaseHelper
                                            .getPaidRentMonths(id)
                                            .forEach(
                                                    e -> {
                                                        List<String> buttonNames = new ArrayList<>();
                                                        buttons.add(buttonNames);
                                                        buttonNames.add(e);
                                                    });

                                    if (!buttons.isEmpty()) {
                                        buttons.add(getButtonsList((isRus) ? "главное меню"
                                                : "main menu"));
                                        rh.setNeedReplyMarkup(true);
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));
                                        answer = (isRus) ? "Следующие месяца могут быть удалены:"
                                                : "Ok. Follow months can be deleted";
                                    } else {
                                        answer = (isRus) ? "Месяца не найдены"
                                                : "Not found months for deleting";
                                        activeCommand.remove(id);
                                    }
                                }
                                break;

                                case "/gethistory": {
                                    answer = dataBaseHelper
                                            .getPaymentsHistory(id, isRus);
                                    if (answer.length() == 0) {
                                        answer = (isRus) ? "История платежей отсутствует"
                                                : "History is empty!";
                                    }
                                }
                                break;
                                case "/purge": {
                                    List<List<String>> buttons = new ArrayList<>();
                                    if (isRus) {
                                        buttons.add(getButtonsList("да"));
                                        buttons.add(getButtonsList("главное меню"));
                                    } else {
                                        buttons.add(getButtonsList("yes"));
                                        buttons.add(getButtonsList("main menu"));
                                    }
                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));
                                    answer = (isRus) ? "Вся информация об аренде будет удалена. Вы уверены?"
                                            : "All information about rent will be remove. Are you sure?";
                                }
                                break;
                                case "/getstat": {
                                    Optional<RentHolder> rentHolder = rentData
                                            .get(id);
                                    answer = (null != rentHolder) ? rentHolder
                                            .get().getStatAddedMonth(isRus)
                                            : (isRus) ? "Режим аренды не активирован. Для активации используйте команду 'добавить месяц'"
                                            : "Rent mode is not active. For activate rent mode use 'add month' command";
                                }
                                break;
                                case "/getstatbymonth": {
                                    List<List<String>> buttons = new ArrayList<>();

                                    dataBaseHelper
                                            .getPaidRentMonths(id)
                                            .forEach(
                                                    e -> {
                                                        List<String> buttonNames = new ArrayList<>();
                                                        buttons.add(buttonNames);
                                                        buttonNames.add(e);
                                                    });

                                    if (!buttons.isEmpty()) {

                                        buttons.add(getButtonsList((isRus) ? "главное меню"
                                                : "main menu"));

                                        rh.setNeedReplyMarkup(true);
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));
                                        answer = (isRus) ? "Выберите месяц для детализации"
                                                : "Ok. Follow months can be detailed";
                                    } else {
                                        answer = (isRus) ? "Месяца не найдены"
                                                : "Not found months for detailed";
                                        activeCommand.remove(id);
                                    }
                                }
                                break;
                                case "/calc": {
                                    Optional<RentHolder> rentHolder = rentData
                                            .get(id);
                                    if (rentHolder.get().isLightSet()
                                            && rentHolder.get().isWaterSet()
                                            && rentHolder.get().getMonthOfRent() != null) {
                                        List<List<String>> buttons = new ArrayList<>();
                                        if (isRus) {
                                            buttons.add(getButtonsList("да", "нет"));
                                        } else {
                                            buttons.add(getButtonsList("yes", "no"));
                                        }
                                        rh.setNeedReplyMarkup(true);
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));

                                        answer = (isRus) ? "Вы оплачиваете водоотвод?"
                                                : "Ok. Do you need include outfall to final amount?";
                                    } else {
                                        answer = (isRus) ? "Показания не заданы. Для расчета необходимо задать название месяца, показания воды и света!"
                                                : "Required parameters not set. For calculating rent amount you must set month name, indications of water and light";
                                    }
                                }
                                break;
                                case "/settakeout": {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Используйте следующий формат:\n\nсумма описание\n(вместо пробелов в описании используйте нижнее подчеркивание)"
                                            : "For set takeout information please use this format:\n\namount description\n\ndescription restriction - use underscore instead of space";
                                }
                                break;
                                case "/setmonth": {
                                    List<List<String>> buttons = new ArrayList<>();
                                    DateTime date = new DateTime();

                                    String month = date.toString("MMMM",
                                            (isRus) ? new Locale("ru", "RU")
                                                    : Locale.US);

                                    if (isRus) {
                                        if (month.charAt(month.length() - 1) == 'я') {
                                            month = month.substring(0,
                                                    month.length() - 1)
                                                    + "ь";
                                        }
                                    }

                                    buttons.add(getButtonsList(month + " "
                                            + date.getYear()));

                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));
                                    answer = (isRus) ? "Используйте кнопку ниже для задания текущего месяца или используйте формат:\n\nназвание год (например, май 2016)"
                                            : "For set rent for current month - tap on button below.\n\nFor set rent for another month please use this format:\n\nmonth year\nExample:may 2016";
                                }
                                break;
                                case "/setlight": {
                                    RentHolder rent = rentData.get(id).get();
                                    Map<String, Double> lightPrimary = rent
                                            .getLightPrimary().getIndications();

                                    if (lightPrimary.size() > 1) {
                                        List<List<String>> buttons = new ArrayList<>();
                                        List<String> buttonNames = new ArrayList<>();
                                        buttons.add(buttonNames);

                                        lightPrimary.entrySet().stream()
                                                .forEach(e -> {
                                                    buttonNames.add(e.getKey());
                                                });

                                        buttons.add(getButtonsList((isRus) ? "главное меню"
                                                : "main menu"));

                                        cacheButtons.put(id, buttons);

                                        rh.setNeedReplyMarkup(true);
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));

                                        answer = (isRus) ? "У вас "
                                                + lightPrimary.size()
                                                + " тарифный счетчик света. Для задания показаний используйте кнопки ниже"
                                                : "You have "
                                                + lightPrimary.size()
                                                + " counter of light. Set the value for it use buttons below";
                                    } else {
                                        hideKeybord(rh);
                                        answer = (isRus) ? "У вас однотарифный счетчик света. Просто задайте значение"
                                                : "You have one-tariff counter of light. Set the value for it";
                                    }

                                }
                                break;
                                case "/setwater": {
                                    Optional<RentHolder> rentHolder = rentData
                                            .get(id);
                                    PrimaryWaterHolder water = rentHolder.get()
                                            .getWaterPrimary();

                                    List<List<String>> buttons = new ArrayList<>();
                                    int size = water.getHotWater().size();
                                    if (size > 1) {
                                        for (Entry<Integer, WaterHolder> e : water
                                                .getHotWater().entrySet()) {
                                            buttons.add(getButtonsList((isRus) ? "горячая-"
                                                    + e.getValue().getAlias()
                                                    : "hot-"
                                                    + e.getValue()
                                                    .getAlias()));
                                        }
                                    } else {
                                        if (size == 1) {
                                            buttons.add(getButtonsList((isRus) ? "горячая"
                                                    : "hot"));
                                        }
                                    }
                                    size = water.getColdWater().size();

                                    if (size > 1) {
                                        for (Entry<Integer, WaterHolder> e : water
                                                .getColdWater().entrySet()) {
                                            buttons.add(getButtonsList((isRus) ? "холодная-"
                                                    + e.getValue().getAlias()
                                                    : "cold-"
                                                    + e.getValue()
                                                    .getAlias()));
                                        }
                                    } else {
                                        if (size == 1) {
                                            buttons.add(getButtonsList((isRus) ? "холодная"
                                                    : "cold"));
                                        }
                                    }
                                    buttons.add(getButtonsList((isRus) ? "главное меню"
                                            : "main menu"));

                                    rentHolder.get().setWaterButtons(buttons);

                                    rh.setNeedReplyMarkup(true);
                                    try {
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));
                                    } catch (JsonProcessingException e) {
                                        log.error(e.getMessage(), e);
                                    }
                                    answer = (isRus) ? "Необходимо задать следующие счетчики"
                                            : "Ok. Set indications for it";
                                }
                                break;
                                case "/setprimarycounters": {
                                    defaultPrimaryButtons(rh, isRus);
                                    answer = (isRus) ? "На данных показаниях будут строиться дальнейшие расчеты аренды.\nВы можете использовать следующие команды:\n\n" +
                                            "<b>вода</b> - задать начальные показания счетчиков воды\n" +
                                            "<b>арендная плата</b> - задать сумму аренды\n" +
                                            "<b>свет</b> - задать начальные показания счетчика света"
                                            : "In these indications will be built further lease payments.\nYou can set next primary counters via follows commands:\n\n" +
                                            "set water (/setprimarywater) - set primary counters for water\n" +
                                            "set rent amount (/setprimaryrentamount) - set rent amount per month\n" +
                                            "set light (/setprimarylight) - set primary counters for light";
                                }

                                break;
                                case "/setprimarywater": {
                                    chatObjectMapper.put(id,
                                            new PrimaryWaterHolder());
                                    List<List<String>> buttons = new ArrayList<>();
                                    if (isRus) {
                                        buttons.add(getButtonsList("горячая",
                                                "холодная"));
                                        buttons.add(getButtonsList("главное меню"));
                                    } else {
                                        buttons.add(getButtonsList("hot", "cold"));
                                        buttons.add(getButtonsList("main menu"));
                                    }

                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));
                                    answer = (isRus) ? "Выберите тип воды"
                                            : "Choose type of water for setting primary indications?";
                                }

                                break;
                                case "/setprimaryrentamount": {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Задайте сумму аренды"
                                            : "Ok. Send simple message with rent amount per month";
                                }
                                break;
                                case "/setprimarylight": {
                                    chatObjectMapper.put(id,
                                            new PrimaryLightHolder());
                                    List<List<String>> buttons = new ArrayList<>();
                                    buttons.add(getButtonsList("1", "2", "3"));
                                    buttons.add(getButtonsList((isRus) ? "главное меню"
                                            : "main menu"));

                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));
                                    answer = (isRus) ? "Какой у вас тип тарификации?\n\n1- однотарифный\n2 - двухтарифный\n3 - трехтарифный"
                                            : "Which type of tariff is right for you?\n\n1- one-tariff\n2 - two-tariff\n3 - three-tariff";
                                }
                                break;
                                default: {
                                }
                                break;
                            }

                        } else {
                            if (null != activeCommand.get(id)) {
                                CommandHolder ch = activeCommand.get(id);
                                answer = processSimpleMessages(id, owner,
                                        ch.getCommand(), text, rh,
                                        ch.isRusLang());
                            }
                        }

                        rh.setResponseMessage(answer);
                        resp.getResponses().add(rh);

                    }
                } catch (Exception e) {
                    StringBuilder sb = new StringBuilder();

                    try {
                        sb.append("sendMessage?chat_id=")
                                .append(id)
                                .append("&parse_mode=HTML&text=")
                                .append(URLEncoder
                                        .encode(getStart(rh), "UTF-8"))
                                .append("&reply_markup=")
                                .append(rh.getReplyMarkup());
                    } catch (UnsupportedEncodingException e1) {
                        log.error(e.getMessage(), e);
                    }
                    callApiGet(sb.toString(), this.telegramApiUrl);
                    if (rentData.containsKey(id)) {
                        rentData.remove(id);
                    }
                    log.error("Can't parse message! Error: %s",
                            e.getMessage(), e);
                }

            }

            resp.setMaxUpdateId(updateId);

        }

        return resp;

    }

    private String processSimpleMessages(String idChat, String owner,
                                         String command, String text, ResponseHolder rh, boolean isRus) {

        boolean isRemoveCommand = true;

        String answer = "";

        switch (command) {
            case "/purge": {
                if (text.equalsIgnoreCase((isRus) ? "да" : "yes")) {
                    List<List<String>> buttons = new ArrayList<>();
                    if (dataBaseHelper.purgeAll(idChat)) {
                        buttons.add(getButtonsList((isRus) ? "главное меню"
                                : "main menu"));
                        rh.setNeedReplyMarkup(true);
                        try {
                            rh.setReplyMarkup(objectMapper
                                    .writeValueAsString(getButtons(buttons)));
                        } catch (JsonProcessingException e) {
                            log.error(e.getMessage(), e);
                        }
                        answer = (isRus) ? "Вся информация об аренде удалена"
                                : "All information about rent removed";
                    } else {
                        answer = "Oops error! Can't delete information!";
                    }
                } else {
                    return (isRus) ? "Неверный формат! Используйте кнопки ниже"
                            : "Wrong format! Use buttons below";
                }
            }
            break;
            case "/changehwrate": {
                Double value = null;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage(), e);
                    return NaN(isRus);
                }

                try {
                    PrimaryWaterHolder waterHolder = objectMapper.readValue(
                            (String) dataBaseHelper.getFirstValue(
                                    "rent_const", "water",
                                    Filters.eq("id_chat", idChat)),
                            PrimaryWaterHolder.class);
                    waterHolder.setHotWaterRate(value);

                    if (dataBaseHelper.updateField("rent_const",
                            idChat, "water",
                            objectMapper.writeValueAsString(waterHolder))) {
                        rh.setNeedReplyMarkup(true);
                        rh.setReplyMarkup(objectMapper
                                .writeValueAsString(getButtons(cacheButtons
                                        .get(idChat))));
                        answer = (isRus) ? "Тариф горячей воды изменен"
                                : "Hot water rate updated successfully!";
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }

            }

            break;
            case "/changecwrate": {
                Double value = null;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage(), e);
                    return NaN(isRus);
                }

                try {
                    PrimaryWaterHolder waterHolder = objectMapper.readValue(
                            (String) dataBaseHelper.getFirstValue(
                                    "rent_const", "water",
                                    Filters.eq("id_chat", idChat)),
                            PrimaryWaterHolder.class);
                    waterHolder.setColdWaterRate(value);

                    if (dataBaseHelper.updateField("rent_const",
                            idChat, "water",
                            objectMapper.writeValueAsString(waterHolder))) {
                        rh.setNeedReplyMarkup(true);
                        rh.setReplyMarkup(objectMapper
                                .writeValueAsString(getButtons(cacheButtons
                                        .get(idChat))));
                        answer = (isRus) ? "Тариф холодной воды изменен"
                                : "Cold water rate updated successfully!";
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }

            }

            break;
            case "/changeoutfallrate": {
                Double value = null;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage(), e);
                    return NaN(isRus);
                }

                try {
                    PrimaryWaterHolder waterHolder = objectMapper.readValue(
                            (String) dataBaseHelper.getFirstValue(
                                    "rent_const", "water",
                                    Filters.eq("id_chat", idChat)),
                            PrimaryWaterHolder.class);
                    waterHolder.setOutfallRate(value);

                    if (dataBaseHelper.updateField("rent_const",
                            idChat, "water",
                            objectMapper.writeValueAsString(waterHolder))) {
                        rh.setNeedReplyMarkup(true);
                        rh.setReplyMarkup(objectMapper
                                .writeValueAsString(getButtons(cacheButtons
                                        .get(idChat))));
                        answer = (isRus) ? "Тариф водоотвода изменен"
                                : "Outfall rate updated successfully!";
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }

            }
            break;
            case "/changelightrate": {
                try {
                    boolean exit = false;
                    PrimaryLightHolder light = (chatObjectMapper.get(idChat) == null) ? objectMapper
                            .readValue(
                                    (String) dataBaseHelper
                                            .getFirstValue("rent_const", "light",
                                                    Filters.eq("id_chat", idChat)),
                                    PrimaryLightHolder.class)
                            : (PrimaryLightHolder) chatObjectMapper.get(idChat);

                    Map<String, Double> lightRates = light.getRates();

                    int sizeRates = lightRates.size();

                    switch (sizeRates) {
                        case 1: {
                            try {
                                exit = true;
                                light.getRates().put("t1", Double.valueOf(text));
                                answer = (isRus) ? "Тариф на электроэнергию изменен"
                                        : "Rate updated successfully!";
                            } catch (NumberFormatException e) {
                                isRemoveCommand = false;
                                return NaN(isRus);
                            }
                        }
                        break;
                        case 2:
                        case 3:
                            Double value = null;
                            try {
                                value = Double.valueOf(text);
                            } catch (NumberFormatException e) {
                                /**
                                 * nothing
                                 */
                            }

                            if (null != value) {
                                String key = null;
                                for (Entry<String, Double> entry : light.getRates()
                                        .entrySet()) {
                                    if (entry.getValue().equals(0.0)) {
                                        entry.setValue(value);
                                        key = entry.getKey();
                                    }
                                }
                                exit = true;
                                answer = (isRus) ? "Тариф " + key + " изменен успешно!"
                                        : "Rate for " + key + " updated successfully!";
                            } else if (light.getRates().containsKey(text.toLowerCase())) {
                                chatObjectMapper.put(idChat, light);
                                hideKeybord(rh);
                                isRemoveCommand = false;
                                light.getRates().put(text, 0.0);
                                answer = (isRus) ? "Установите новое значения для тарифа "
                                        + text
                                        : "Please set new value for " + text;
                            } else {
                                isRemoveCommand = false;
                                return (isRus) ? "Неверный формат! Для выбора тарифа используйте кнопки ниже"
                                        : "Wrong format! Use buttons below for choosing tariff. Try again";
                            }
                            break;
                        default:
                            break;
                    }

                    if (exit) {
                        dataBaseHelper.updateField("rent_const",
                                idChat, "light",
                                objectMapper.writeValueAsString(light));
                        rh.setNeedReplyMarkup(true);
                        rh.setReplyMarkup(objectMapper
                                .writeValueAsString(getButtons(cacheButtons
                                        .get(idChat))));
                    }

                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
            break;
            case "/changera": {
                Double value = null;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage(), e);
                    return NaN(isRus);
                }

                if (dataBaseHelper.updateField("rent_const", idChat,
                        "rent_amount", value)) {
                    answer = (isRus) ? "Сумма аренды обновлена"
                            : "Rent amount rate updated successfully!";
                    rh.setNeedReplyMarkup(true);
                    try {
                        rh.setReplyMarkup(objectMapper
                                .writeValueAsString(getButtons(cacheButtons
                                        .get(idChat))));
                    } catch (JsonProcessingException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
            break;
            case "/delmonthstat":
                if (dataBaseHelper.deleteMothStat(idChat,
                        text.toLowerCase())) {
                    setDefaultRentButtons(rh, isRus);
                    answer = (isRus) ? "Статистика за " + text + " удалена"
                            : "Statistics of " + text + " deleted successfully!";
                } else {
                    return (isRus) ? "Запрошенный месяц не найден! Используйте кнопки ниже для выбора месяца"
                            : "Asked a month is not found! Use buttons below for choose a month";
                }
                break;
            case "/getstatbymonth": {
                answer = dataBaseHelper.getStatByMonth(text, idChat,
                        isRus);
                if (answer.length() == 0) {
                    return (isRus) ? "Запрошенный месяц не найден! Используйте кнопки ниже для выбора месяца"
                            : "Asked a month is not found! Use buttons below for choose a month";
                } else {
                    monthForEdit.put(idChat, text);
                    setDefaultRentButtons(rh, isRus);
                }
            }

            break;
            case "/calc": {
                if (checkStrByRegexp(text, "[a-zа-я]{2,3}$")) {
                    setDefaultRentButtons(rh, isRus);
                    Boolean outfall = (text
                            .equalsIgnoreCase((isRus) ? "да" : "yes")) ? true
                            : false;
                    Optional<RentHolder> rentHolder = rentData.get(idChat);
                    Optional<String> answerTemp = (rentHolder != null) ? Optional
                            .of(String.valueOf(rentHolder.get().getTotalAmount(
                                    outfall))) : Optional.ofNullable(null);

                    if (answerTemp.isPresent()) {
                        answer = String.format("%.2f",
                                Double.valueOf(answerTemp.get()))
                                + ((isRus) ? " руб" : " rub");
                        rentData.remove(idChat);
                    } else {
                        answer = (isRus) ? "Вода и свет не заданы. Необходимо задать показания воды и света"
                                : "Water and light didn't set. You need set water and light indications";
                    }
                } else {
                    return (isRus) ? "Неверный формат! Используйте кнопки ниже"
                            : "Wrong format! Use buttons below";
                }
            }
            break;
            case "/settakeout": {
                String[] data = text.split(" ");
                Optional<RentHolder> rentHolder = rentData.get(idChat);

                if (null != rentHolder) {
                    if (data.length == 2) {
                        Double takeout = null;
                        try {
                            takeout = Double.parseDouble(data[0]);
                        } catch (NumberFormatException e) {
                            log.error(e.getMessage(), e);
                            return NaN(isRus);
                        }
                        rentHolder.get().setTakeout(takeout);
                        rentHolder.get().setTakeoutDescription(data[1]);
                        answer = (isRus) ? "Вычет задан успешно"
                                : "Takeout set successfully!";
                        defaultAddMonthButtons(rentHolder.get(), rh, isRus);
                    } else {
                        return (isRus) ? "Неверный формат! Используйте формат:\n\nсумма описание (вместо пробелов в описании используйте нижнее подчеркивание)"
                                : "Wrong format! Please use this format:\n\n amount description\n\ndescription restriction - use underscore instead of space";
                    }
                } else {
                    answer = (isRus) ? "Режим аренды не активирован. Для активации используйте команду 'добавить месяц'"
                            : "Rent mode is not active. For activate rent mode use 'add month' command";
                }
            }
            break;
            case "/setmonth": {
                if (text.split(" ").length == 2) {
                    Optional<RentHolder> rentHolder = rentData.get(idChat);

                    if (null != rentHolder) {
                        rentHolder.get().setMonthOfRent(text);
                        answer = (isRus) ? "Месяц "
                                + rentHolder.get().getMonthOfRent()
                                + " задан успешно!" : "Month "
                                + rentHolder.get().getMonthOfRent()
                                + " set successfully!";
                        defaultAddMonthButtons(rentHolder.get(), rh, isRus);
                    } else {
                        answer = (isRus) ? "Режим аренды не активирован. Для активации используйте команду 'добавить месяц'"
                                : "Rent mode is not active. For activate rent mode use 'add month' command";
                    }
                } else {
                    return (isRus) ? "Неверный формат! Используйте формат:\n\nназвание год (например, май 2016)"
                            : "Wrong format! Please use this format:\n\n month year (may 2016)";
                }
            }
            break;
            case "/setlight": {
                isRemoveCommand = false;
                RentHolder rentHolder = rentData.get(String.valueOf(idChat)).get();

                Map<String, Double> lightPrimary = rentHolder.getLightPrimary()
                        .getIndications();

                int sizeLightPrimary = lightPrimary.size();

                if (sizeLightPrimary == 1) {
                    try {
                        rentHolder.getCurrentLightIndications().put("t1",
                                Double.parseDouble(text));
                    } catch (NumberFormatException e) {
                        log.error(e.getMessage(), e);
                        return NaN(isRus);
                    }

                    if (rentHolder.isLightSet()) {
                        isRemoveCommand = true;
                        answer = (isRus) ? "Показания электричества заданы успешно"
                                : "Light set successfully";
                        defaultAddMonthButtons(rentHolder, rh, isRus);
                    }

                } else if (rentHolder.getLightPrimary().getIndications()
                        .containsKey(text)) {
                    rentHolder.getCurrentLightIndications().put(text, 0.0);
                    rentHolder.setLightTypeActive(text);

                    hideKeybord(rh);
                    answer = (isRus) ? "Задайте показание для периода " + text
                            : "Set light indication for " + text;

                } else {
                    String lightActiveType = rentHolder.getLightTypeActive();

                    if (null != lightActiveType) {
                        try {
                            rentHolder.getCurrentLightIndications().put(
                                    lightActiveType, Double.parseDouble(text));
                            answer = (isRus) ? "Показание для периода "
                                    + lightActiveType + " задано успешно!"
                                    : "Indication for " + lightActiveType
                                    + " set successfully!";
                            markDoneButton(idChat, lightActiveType, rh);
                        } catch (NumberFormatException e) {
                            log.error(e.getMessage(), e);
                            return NaN(isRus);
                        }
                    } else {
                        return (isRus) ? "Выберите период используя кнопки ниже"
                                : "Choose type of light (buttons below)";
                    }

                    if (rentHolder.isLightSet()) {
                        isRemoveCommand = true;
                        answer = (isRus) ? "Показания электричества заданы успешно"
                                : "Light set successfully";
                        defaultAddMonthButtons(rentHolder, rh, isRus);
                    }
                }
            }
            break;
            case "/setwater": {
                isRemoveCommand = false;
                RentHolder rentHolder = rentData.get(idChat).get();
                if (null != rentHolder.getWaterTypeActive()) {

                    Double counterValue = null;

                    try {
                        counterValue = Double.parseDouble(text);
                    } catch (NumberFormatException e) {
                        return NaN(isRus);
                    }

                    // wait value for indication
                    switch (rentHolder.getWaterTypeActive()) {
                        case "hot":
                        case "горячая":
                            ((NavigableMap<Integer, WaterHolder>) rentHolder
                                    .getCurrentHotWaterIndications()).lastEntry()
                                    .getValue().setPrimaryIndication(counterValue);
                            break;
                        case "cold":
                        case "холодная":
                            ((NavigableMap<Integer, WaterHolder>) rentHolder
                                    .getCurrentColdWaterIndications()).lastEntry()
                                    .getValue().setPrimaryIndication(counterValue);
                            break;
                        default:
                            break;
                    }

                    answer = (isRus) ? rentHolder.getWaterTypeActive()
                            + " вода задана успешно" : rentHolder
                            .getWaterTypeActive() + " set successfully";
                    defaultWaterButtons(rentHolder, rh,
                            rentHolder.getButtonWaterCounterActive());

                    rentHolder.setWaterTypeActive(null);

                    if (rentHolder.isWaterSet()) {
                        isRemoveCommand = true;
                        answer = (isRus) ? "Показания воды заданы успешно"
                                : "Water set successfully";
                        defaultAddMonthButtons(rentHolder, rh, isRus);
                    }

                } else {
                    String[] temp = text.split("-");
                    int tempSize = temp.length;

                    switch (temp[0]) {
                        case "hot":
                        case "горячая": {
                            Map<Integer, WaterHolder> hotWater = rentHolder
                                    .getCurrentHotWaterIndications();
                            if (tempSize > 1) {
                                // with alias
                                if (hotWater.isEmpty()) {
                                    hotWater.put(1, new WaterHolder(temp[1]));
                                    rentHolder.getAddedWater().put(text, 1);
                                } else {
                                    if (rentHolder.getAddedWater().containsKey(text)) {
                                        Integer indexExist = rentHolder.getAddedWater()
                                                .get(text);

                                        hotWater.put(indexExist, new WaterHolder(
                                                temp[1]));

                                    } else {
                                        Integer next = ((NavigableMap<Integer, WaterHolder>) hotWater)
                                                .lastKey() + 1;

                                        hotWater.put(next, new WaterHolder(temp[1]));
                                        rentHolder.getAddedWater().put(text, next);
                                    }
                                }
                            } else {
                                hotWater.put(1, new WaterHolder());
                            }
                            rentHolder.setWaterTypeActive((isRus) ? "горячая" : "hot");

                        }
                        break;
                        case "cold":
                        case "холодная": {
                            Map<Integer, WaterHolder> coldWater = rentHolder
                                    .getCurrentColdWaterIndications();
                            if (tempSize > 1) {

                                // with alias
                                if (coldWater.isEmpty()) {
                                    coldWater.put(1, new WaterHolder(temp[1]));
                                    rentHolder.getAddedWater().put(text, 1);

                                } else {
                                    if (rentHolder.getAddedWater().containsKey(text)) {
                                        Integer indexExist = rentHolder.getAddedWater()
                                                .get(text);

                                        coldWater.put(indexExist, new WaterHolder(
                                                temp[1]));

                                    } else {
                                        Integer next = ((NavigableMap<Integer, WaterHolder>) coldWater)
                                                .lastKey() + 1;
                                        coldWater.put(next, new WaterHolder(temp[1]));
                                        rentHolder.getAddedWater().put(text, next);
                                    }
                                }

                            } else {
                                coldWater.put(1, new WaterHolder());
                            }
                            rentHolder
                                    .setWaterTypeActive((isRus) ? "холодная" : "cold");
                        }
                        break;
                        default:
                            return (isRus) ? "Неверный формат! Используйте кнопки ниже"
                                    : "Wrong format! Use button below";
                    }

                    answer = (isRus) ? "Задайте значение для " + text + " вода"
                            : "Set value for " + text;
                    rentHolder.setButtonWaterCounterActive(text);
                    hideKeybord(rh);
                }
            }
            break;
            case "/setprimarywater": {
                isRemoveCommand = false;
                PrimaryWaterHolder pwh = (PrimaryWaterHolder) chatObjectMapper
                        .get(idChat);

                if (text.equalsIgnoreCase((isRus) ? "назад" : "back")) {
                    List<List<String>> buttons = new ArrayList<>();

                    boolean isSetHot = (pwh.getHotWater() != null && pwh
                            .getHotWater().size() == pwh.getCountHotWaterCounter()) ? true
                            : false;
                    boolean isSetCold = (pwh.getColdWater() != null && pwh
                            .getColdWater().size() == pwh
                            .getCountColdWaterCounter()) ? true : false;

                    if (isRus) {
                        buttons.add(getButtonsList((isSetHot) ? getEmoji("E29C85")
                                        + " горячая" : "горячая",
                                (isSetCold) ? getEmoji("E29C85") + " холодная"
                                        : "холодная"));
                        buttons.add(getButtonsList("главное меню"));
                    } else {
                        buttons.add(getButtonsList((isSetHot) ? getEmoji("E29C85")
                                + " hot" : "hot", (isSetCold) ? getEmoji("E29C85")
                                + " cold" : "cold"));
                        buttons.add(getButtonsList("main menu"));
                    }

                    rh.setNeedReplyMarkup(true);
                    try {
                        rh.setReplyMarkup(objectMapper
                                .writeValueAsString(getButtons(buttons)));
                    } catch (JsonProcessingException e) {
                        log.error(e.getMessage(), e);
                    }

                    pwh.setWaitValue(false);
                    pwh.setWaterSet(false);
                    pwh.setTypeOfWater(null);
                    return (isRus) ? "Выберите тип воды"
                            : "Choose type of water for setting primary indications?";
                }

                String typeOfWater = pwh.getTypeOfWater();

                if (null == typeOfWater) {

                    // check back
                    if ((pwh.getCountColdWaterCounter() != null && pwh
                            .getCountHotWaterCounter() != null)
                            || (text.equalsIgnoreCase((isRus) ? "горячая" : "hot")
                            && (pwh.getCountHotWaterCounter() != null) && pwh
                            .getCountHotWaterCounter() > 0)
                            || (text.equalsIgnoreCase((isRus) ? "холодная" : "cold") && (pwh
                            .getCountColdWaterCounter() != null))) {

                        List<List<String>> buttons = new ArrayList<>();
                        Map<Integer, WaterHolder> waterHolder;
                        log.trace("Back button was tap");
                        Integer countCounters = 0;
                        switch (text) {
                            case "hot":
                            case "горячая": {
                                countCounters = pwh.getCountHotWaterCounter();
                                pwh.setTypeOfWater(text);
                                waterHolder = pwh.getHotWater();

                            }
                            break;
                            case "cold":
                            case "холодная": {
                                countCounters = pwh.getCountColdWaterCounter();
                                pwh.setTypeOfWater(text);
                                waterHolder = pwh.getColdWater();
                            }
                            break;

                            default: {
                                activeCommand.remove(idChat);
                                defaultPrimaryButtons(rh, isRus);
                                return (isRus) ? "Неверный формат! Вода может быть горячей или холодной. Задайте начальные значения заново"
                                        : "Wrong format! Water can be hot or cold. Set primary water indications and rates again";
                            }
                        }

                        if (countCounters > 1) {
                            if (null != waterHolder) {
                                int addCount = 0;
                                for (Entry<Integer, WaterHolder> entry : waterHolder
                                        .entrySet()) {

                                    if (entry.getValue().getAlias() != null) {
                                        buttons.add(getButtonsList(getEmoji("E29C85")
                                                + " " + entry.getValue().getAlias()));
                                    } else {
                                        buttons.add(getButtonsList(entry.getKey()
                                                .toString().toLowerCase()));
                                    }
                                    ++addCount;
                                }

                                if (addCount < countCounters) {
                                    for (int i = addCount; i < countCounters; i++) {
                                        buttons.add(getButtonsList(String
                                                .valueOf(i + 1)));
                                    }
                                }

                            } else {
                                for (int i = 0; i < countCounters; i++) {
                                    buttons.add(getButtonsList(String
                                            .valueOf(i + 1)));
                                }

                            }

                            if (isRus) {
                                buttons.add(getButtonsList("назад"));
                                buttons.add(getButtonsList("главное меню"));
                            } else {
                                buttons.add(getButtonsList("back"));
                                buttons.add(getButtonsList("main menu"));
                            }

                            cacheButtons.put(idChat, buttons);

                            rh.setNeedReplyMarkup(true);
                            try {
                                rh.setReplyMarkup(objectMapper
                                        .writeValueAsString(getButtons(buttons)));
                            } catch (JsonProcessingException e) {
                                log.error(e.getMessage(), e);
                            }

                            answer = (isRus) ? new StringBuilder("У вас ")
                                    .append(countCounters).append(" счетчика")
                                    .append(". Задайте начальные значения для них")
                                    .toString()
                                    : new StringBuilder("Ok. You have ")
                                    .append(countCounters)
                                    .append(" counter of ")
                                    .append(pwh.getTypeOfWater())
                                    .append(" water. Set primary indications for it")
                                    .toString();

                        } else {
                            hideKeybord(rh);
                            pwh.setWaitValue(true);
                            answer = (isRus) ? "Задайте начальное значение"
                                    : "Set primary indication";
                        }
                        pwh.setWaterSet(true);

                    } else {

                        Integer existCounters = 0;

                        switch (text) {
                            case "hot":
                            case "горячая":
                                existCounters = pwh.getCountColdWaterCounter();
                                pwh.setTypeOfWater(text);
                                break;
                            case "cold":
                            case "холодная":
                                existCounters = pwh.getCountHotWaterCounter();
                                pwh.setTypeOfWater(text);
                                break;

                            default: {
                                activeCommand.remove(idChat);
                                defaultPrimaryButtons(rh, isRus);
                                return (isRus) ? "Неверный формат! Вода может быть горячей или холодной. Задайте начальные значения заново"
                                        : "Wrong format! Water can be hot or cold. Set primary water indications and rates again";
                            }
                        }
                        StringBuilder sb = (isRus) ? new StringBuilder(
                                "Сколько у вас счетчиков? (Максимум: 5)")
                                : new StringBuilder("What number of counters for ")
                                .append(text).append(
                                        " water you have? (Maximum: 5)");

                        if (null != existCounters) {
                            List<List<String>> buttons = new ArrayList<>();
                            if (existCounters == 0) {
                                ++existCounters;
                            }
                            buttons.add(getButtonsList(existCounters.toString()));
                            rh.setNeedReplyMarkup(true);
                            try {
                                rh.setReplyMarkup(objectMapper
                                        .writeValueAsString(getButtons(buttons)));
                            } catch (JsonProcessingException e) {
                                log.error(e.getMessage(), e);
                            }
                            answer = (isRus) ? sb.append("\n\nВозможно у вас ")
                                    .append(existCounters).append(" счетчик(а)?")
                                    .toString() : sb.append("\n\nMaybe you have ")
                                    .append(existCounters).append(" counter of ")
                                    .append(text).append(" water").toString();
                        } else {
                            answer = sb.toString();
                            hideKeybord(rh);
                        }
                    }
                } else {
                    if (pwh.isWaterSet()) {

                        // create water objects
                        switch (typeOfWater) {
                            case "hot":
                            case "горячая": {
                                if ((null == pwh.getHotWater() || pwh.getHotWater()
                                        .size() <= pwh.getCountHotWaterCounter())
                                        && !pwh.isWaitValue()) {

                                    Integer counter = null;
                                    Double value = null;

                                    try {
                                        StringBuilder sb = new StringBuilder();

                                        if (null != pwh.getHotWater()) {
                                            pwh.getHotWater()
                                                    .entrySet()
                                                    .stream()
                                                    .forEach(
                                                            e -> {
                                                                if (text.equalsIgnoreCase(e
                                                                        .getValue()
                                                                        .getAlias())) {
                                                                    sb.append(e
                                                                            .getKey());
                                                                }
                                                            });
                                        }

                                        if (pwh.getCountHotWaterCounter() > 1) {
                                            counter = (sb.toString().length() > 0) ? Integer
                                                    .valueOf(sb.toString()) : Integer
                                                    .valueOf(text);
                                        } else {
                                            // one-tariff counter
                                            try {
                                                value = Double.valueOf(text);
                                            } catch (NumberFormatException e) {
                                                return NaN(isRus);
                                            }
                                        }

                                        if (pwh.getCountHotWaterCounter() > 1
                                                && counter > pwh
                                                .getCountHotWaterCounter()) {
                                            return (isRus) ? "Неверный формат! Используйте кнопки ниже для выбора счетчика"
                                                    : "Wrong format! Try again! Use buttons below to choose counter";
                                        }
                                    } catch (NumberFormatException e) {
                                        return (isRus) ? "Неверный формат! Используйте кнопки ниже для выбора счетчика"
                                                : "Wrong format! Try again! Use buttons below to choose counter";
                                    }

                                    pwh.setHotWater();

                                    if (pwh.getCountHotWaterCounter() > 1) {
                                        int waitCounter = pwh.getHotWater().size() + 1;
                                        if (counter != waitCounter
                                                && !pwh.getHotWater().containsKey(
                                                counter)) {
                                            return (isRus) ? "Задайте сначала счетчик номер "
                                                    + waitCounter
                                                    : "Set before counter number "
                                                    + waitCounter;
                                        }
                                        pwh.getHotWater().put(counter,
                                                new WaterHolder(typeOfWater));
                                        answer = (isRus) ? new StringBuilder(
                                                "Задайте начальное значение для счетчика номер ")
                                                .append(counter)
                                                .append(".\n\nИспользуйте формат: значение пседоним (например, 100 ванная)")
                                                .toString()
                                                : new StringBuilder(
                                                "Ok. Set primary indication for counter number ")
                                                .append(counter)
                                                .append(".\nPlease use this format: value alias")
                                                .toString();
                                        hideKeybord(rh);
                                        pwh.setWaitValue(true);

                                    } else {

                                        pwh.getHotWater().put(1,
                                                new WaterHolder(typeOfWater));
                                        pwh.getHotWater().get(1)
                                                .setPrimaryIndication(value);

                                        pwh.setWaterSet(false);
                                        pwh.setTypeOfWater(null);

                                        List<List<String>> buttons = new ArrayList<>();
                                        if (isRus) {
                                            buttons.add(getButtonsList(
                                                    getEmoji("E29C85") + " "
                                                            + "горячая", "холодная"));
                                            buttons.add(getButtonsList("главное меню"));
                                        } else {
                                            buttons.add(getButtonsList(
                                                    getEmoji("E29C85") + " " + "hot",
                                                    "cold"));
                                            buttons.add(getButtonsList("main menu"));
                                        }

                                        rh.setNeedReplyMarkup(true);
                                        try {
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(buttons)));
                                        } catch (JsonProcessingException e) {
                                            log.error(e.getMessage(), e);
                                        }

                                        answer = (isRus) ? "Показания горячей воды заданы успешно"
                                                : "Indications for hot water set successfully";

                                    }

                                } else {
                                    Integer lastKey = 1;

                                    if (pwh.getHotWater().size() > 1) {
                                        for (Entry<Integer, WaterHolder> e : pwh
                                                .getHotWater().entrySet()) {
                                            if (e.getValue().getPrimaryIndication() == null) {
                                                lastKey = e.getKey();
                                            }
                                        }
                                    }

                                    String existAlias = Optional
                                            .ofNullable(pwh.getHotWater().get(lastKey))
                                            .orElse(new WaterHolder()).getAlias();

                                    Double value = null;

                                    if (pwh.getCountHotWaterCounter() > 1) {
                                        String[] temp = text.trim().split(" ");

                                        if (temp.length != 2) {
                                            return (isRus) ? "Неверный формат! Используйте формат: значение псевдоним"
                                                    : "Wrong format! Format must be value alias. Try again";
                                        }

                                        try {
                                            value = Double.valueOf(temp[0]);
                                        } catch (NumberFormatException e) {
                                            return NaN(isRus);
                                        }

                                        pwh.getHotWater().get(lastKey)
                                                .setPrimaryIndication(value);
                                        // check exist alias
                                        String alias = temp[1];

                                        if (lastKey > 1) {
                                            for (Entry<Integer, WaterHolder> e : pwh
                                                    .getHotWater().entrySet()) {
                                                if (e.getKey() < lastKey) {
                                                    if (e.getValue().getAlias()
                                                            .equalsIgnoreCase(alias)) {
                                                        return (isRus) ? "Псевдоним существует! Задайте другой"
                                                                : "Alias exist! Set another";
                                                    }
                                                }
                                            }
                                        }

                                        pwh.getHotWater().get(lastKey).setAlias(alias);

                                        ListIterator<String> iter = cacheButtons
                                                .get(idChat).get(lastKey - 1)
                                                .listIterator();

                                        while (iter.hasNext()) {
                                            String e = iter.next();
                                            if (e.equalsIgnoreCase(lastKey.toString())
                                                    || null != existAlias) {
                                                iter.set(e.replace(e,
                                                        getEmoji("E29C85") + " "
                                                                + temp[1]));
                                            }
                                        }

                                        try {
                                            rh.setNeedReplyMarkup(true);
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(cacheButtons
                                                            .get(idChat))));
                                        } catch (JsonProcessingException e) {
                                            log.error(e.getMessage(), e);
                                        }

                                    } else {

                                        try {
                                            value = Double.valueOf(text);
                                        } catch (NumberFormatException e) {
                                            return NaN(isRus);
                                        }

                                        pwh.getHotWater().get(lastKey)
                                                .setPrimaryIndication(value);
                                    }

                                    answer = (isRus) ? new StringBuilder(
                                            "Счетчик номер ").append(lastKey)
                                            .append(" задан успешно").toString()
                                            : new StringBuilder(
                                            "Ok. Value for counter number ")
                                            .append(lastKey)
                                            .append(" is set successful")
                                            .toString();

                                    pwh.setWaitValue(false);

                                    if (pwh.getHotWater().size() == pwh
                                            .getCountHotWaterCounter()) {
                                        pwh.setWaterSet(false);
                                        pwh.setTypeOfWater(null);

                                        List<List<String>> buttons = new ArrayList<>();
                                        if (isRus) {
                                            buttons.add(getButtonsList(
                                                    getEmoji("E29C85") + " "
                                                            + "горячая", "холодная"));
                                            buttons.add(getButtonsList("главное меню"));
                                        } else {
                                            buttons.add(getButtonsList(
                                                    getEmoji("E29C85") + " " + "hot",
                                                    "cold"));
                                            buttons.add(getButtonsList("main menu"));
                                        }

                                        rh.setNeedReplyMarkup(true);
                                        try {
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(buttons)));
                                        } catch (JsonProcessingException e) {
                                            log.error(e.getMessage(), e);
                                        }

                                        answer = (isRus) ? "Показания горячей воды заданы успешно"
                                                : "Indications for hot water set successfully";

                                    }

                                }

                            }
                            break;
                            case "cold":
                            case "холодная": {
                                if ((null == pwh.getColdWater() || pwh.getColdWater()
                                        .size() <= pwh.getCountColdWaterCounter())
                                        && !pwh.isWaitValue()) {

                                    Integer counter = null;
                                    Double value = null;
                                    try {
                                        StringBuilder sb = new StringBuilder();

                                        if (null != pwh.getColdWater()) {
                                            pwh.getColdWater()
                                                    .entrySet()
                                                    .stream()
                                                    .forEach(
                                                            e -> {
                                                                if (text.equalsIgnoreCase(e
                                                                        .getValue()
                                                                        .getAlias())) {
                                                                    sb.append(e
                                                                            .getKey());
                                                                }
                                                            });
                                        }

                                        if (pwh.getCountColdWaterCounter() > 1) {
                                            counter = (sb.toString().length() > 0) ? Integer
                                                    .valueOf(sb.toString()) : Integer
                                                    .valueOf(text);
                                        } else {
                                            // one-tariff counter
                                            try {
                                                value = Double.valueOf(text);
                                            } catch (NumberFormatException e) {
                                                return NaN(isRus);
                                            }
                                        }

                                        if (pwh.getCountColdWaterCounter() > 1
                                                && counter > pwh
                                                .getCountColdWaterCounter()) {
                                            return (isRus) ? "Неверный формат! Используйте кнопки ниже для выбора счетчика"
                                                    : "Wrong format! Try again! Use buttons below to choose counter";
                                        }
                                    } catch (NumberFormatException e) {
                                        return (isRus) ? "Неверный формат! Используйте кнопки ниже для выбора счетчика"
                                                : "Wrong format! Try again! Use buttons below to choose counter";
                                    }

                                    pwh.setColdWater();

                                    if (pwh.getCountColdWaterCounter() > 1) {
                                        int waitCounter = pwh.getColdWater().size() + 1;
                                        if (counter != waitCounter
                                                && !pwh.getColdWater().containsKey(
                                                counter)) {
                                            return (isRus) ? "Задайте сначала счетчик номер "
                                                    + waitCounter
                                                    : "Set before counter number "
                                                    + (waitCounter);
                                        }
                                        pwh.getColdWater().put(counter,
                                                new WaterHolder(typeOfWater));
                                        answer = (isRus) ? new StringBuilder(
                                                "Задайте начальное значение для счетчика номер ")
                                                .append(counter)
                                                .append(".\n\nИспользуйте формат: значение пседоним (например, 100 ванная")
                                                .toString()
                                                : new StringBuilder(
                                                "Ok. Set primary indication for counter number ")
                                                .append(counter)
                                                .append(".\nPlease use this format: value alias")
                                                .toString();
                                        hideKeybord(rh);
                                        pwh.setWaitValue(true);
                                    } else {
                                        pwh.getColdWater().put(1,
                                                new WaterHolder(typeOfWater));
                                        pwh.getColdWater().get(1)
                                                .setPrimaryIndication(value);

                                        pwh.setWaterSet(false);
                                        pwh.setTypeOfWater(null);

                                        List<List<String>> buttons = new ArrayList<>();

                                        if (isRus) {
                                            buttons.add(getButtonsList("горячая",
                                                    getEmoji("E29C85") + " "
                                                            + "холодная"));
                                            buttons.add(getButtonsList("главное меню"));
                                        } else {
                                            buttons.add(getButtonsList("hot",
                                                    getEmoji("E29C85") + " " + "cold"));
                                            buttons.add(getButtonsList("main menu"));
                                        }

                                        rh.setNeedReplyMarkup(true);
                                        try {
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(buttons)));
                                        } catch (JsonProcessingException e) {
                                            log.error(e.getMessage(), e);
                                        }

                                        answer = (isRus) ? "Показания холодной воды заданы успешно"
                                                : "Indications for cold water set successfully";
                                    }
                                } else {
                                    Integer lastKey = 1;

                                    if (pwh.getColdWater().size() > 1) {
                                        for (Entry<Integer, WaterHolder> e : pwh
                                                .getColdWater().entrySet()) {
                                            if (e.getValue().getPrimaryIndication() == null) {
                                                lastKey = e.getKey();
                                            }
                                        }
                                    }

                                    String existAlias = pwh.getColdWater().get(lastKey)
                                            .getAlias();

                                    Double value = null;

                                    if (pwh.getCountColdWaterCounter() > 1) {
                                        String[] temp = text.trim().split(" ");

                                        if (temp.length != 2) {
                                            return (isRus) ? "Неверный формат! Используйте формат: значение псевдоним"
                                                    : "Wrong format! Format must be value alias. Try again";
                                        }
                                        try {
                                            value = Double.valueOf(temp[0]);
                                        } catch (NumberFormatException e) {
                                            return NaN(isRus);
                                        }

                                        pwh.getColdWater().get(lastKey)
                                                .setPrimaryIndication(value);
                                        // check exist alias
                                        String alias = temp[1];

                                        if (lastKey > 1) {
                                            for (Entry<Integer, WaterHolder> e : pwh
                                                    .getColdWater().entrySet()) {
                                                if (e.getKey() < lastKey) {
                                                    if (e.getValue().getAlias()
                                                            .equalsIgnoreCase(alias)) {
                                                        return (isRus) ? "Псевдоним существует! Задайте другой"
                                                                : "Alias exist! Set another";
                                                    }
                                                }
                                            }
                                        }

                                        pwh.getColdWater().get(lastKey).setAlias(alias);

                                        ListIterator<String> iter = cacheButtons
                                                .get(idChat).get(lastKey - 1)
                                                .listIterator();

                                        while (iter.hasNext()) {
                                            String e = iter.next();
                                            if (e.equalsIgnoreCase(lastKey.toString())
                                                    || null != existAlias) {
                                                iter.set(e.replace(e,
                                                        getEmoji("E29C85") + " "
                                                                + temp[1]));
                                            }
                                        }

                                        try {
                                            rh.setNeedReplyMarkup(true);
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(cacheButtons
                                                            .get(idChat))));
                                        } catch (JsonProcessingException e) {
                                            log.error(e.getMessage(), e);
                                        }

                                    } else {

                                        try {
                                            value = Double.valueOf(text);
                                        } catch (NumberFormatException e) {
                                            return NaN(isRus);
                                        }

                                        pwh.getColdWater().get(lastKey)
                                                .setPrimaryIndication(value);
                                    }

                                    answer = (isRus) ? new StringBuilder(
                                            "Счетчик номер ").append(lastKey)
                                            .append(" задан успешно").toString()
                                            : new StringBuilder("Ok. Counter number ")
                                            .append(lastKey)
                                            .append(" is set successful")
                                            .toString();

                                    pwh.setWaitValue(false);

                                    if (pwh.getColdWater().size() == pwh
                                            .getCountColdWaterCounter()) {
                                        pwh.setWaterSet(false);
                                        pwh.setTypeOfWater(null);

                                        List<List<String>> buttons = new ArrayList<>();
                                        if (isRus) {
                                            buttons.add(getButtonsList("горячая",
                                                    getEmoji("E29C85") + " "
                                                            + "холодная"));
                                            buttons.add(getButtonsList("главное меню"));
                                        } else {
                                            buttons.add(getButtonsList("hot",
                                                    getEmoji("E29C85") + " " + "cold"));
                                            buttons.add(getButtonsList("main menu"));
                                        }

                                        rh.setNeedReplyMarkup(true);
                                        try {
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(buttons)));
                                        } catch (JsonProcessingException e) {
                                            log.error(e.getMessage(), e);
                                        }

                                        answer = (isRus) ? "Показания холодной воды заданы успешно"
                                                : "Indications for cold water set successfully";

                                    }
                                }

                            }
                            break;
                            default:
                                break;
                        }

                        // check finish set
                        if (pwh.isSetWaterIndications() && !pwh.isWaitValue()) {
                            List<List<String>> buttons = new ArrayList<>();
                            List<String> names = new ArrayList<>();
                            String[] temp = {};

                            if (pwh.getCountHotWaterCounter() > 0) {
                                names.add((isRus) ? "горячая" : "hot");
                            } else {
                                pwh.setHotWaterRate(0.0);
                            }

                            if (pwh.getCountColdWaterCounter() > 0) {
                                names.add((isRus) ? "холодная" : "cold");
                            } else {
                                pwh.setColdWaterRate(0.0);
                            }

                            names.add((isRus) ? "водоотведение" : "outfall rate");

                            buttons.add(getButtonsList(names.toArray(temp)));
                            buttons.add(getButtonsList((isRus) ? "главное меню"
                                    : "main menu"));

                            cacheButtons.put(idChat, buttons);

                            rh.setNeedReplyMarkup(true);
                            try {
                                rh.setReplyMarkup(objectMapper
                                        .writeValueAsString(getButtons(buttons)));
                            } catch (JsonProcessingException e) {

                                log.error(e.getMessage(), e);
                            }

                            answer = (isRus) ? "Показания горячей и холодной воды заданы. Теперь необходимо задать тарифы"
                                    : "Indications for cold and hot water set successfully! Set rates for it";
                            pwh.setRatesSet(true);
                            pwh.setTypeOfWater("rate");
                        }

                    } else {

                        if (pwh.isRatesSet()) {

                            if (!pwh.isWaitValue()) {
                                switch (text) {
                                    case "hot":
                                    case "cold":
                                    case "outfall rate":
                                    case "горячая":
                                    case "холодная":
                                    case "водоотведение":
                                        hideKeybord(rh);
                                        pwh.setTypeOfRates(text);
                                        pwh.setWaitValue(true);
                                        answer = (isRus) ? "Задайте тариф"
                                                : "Ok. Set rate for " +
                                                text + " water";
                                        break;

                                    default:
                                        break;
                                }
                            } else {
                                Double rate = null;

                                try {
                                    rate = Double.valueOf(text);
                                } catch (NumberFormatException e) {
                                    return NaN(isRus);
                                }

                                switch (pwh.getTypeOfRates()) {
                                    case "hot":
                                    case "горячая":
                                        pwh.setHotWaterRate(rate);
                                        break;
                                    case "cold":
                                    case "холодная":
                                        pwh.setColdWaterRate(rate);
                                        break;
                                    case "outfall rate":
                                    case "водоотведение":
                                        pwh.setOutfallRate(rate);
                                        break;
                                    default:
                                        break;
                                }
                                answer = (isRus) ? "Тариф задан успешно"
                                        : "Ok. Rate for " +
                                        pwh.getTypeOfRates() +
                                        " water set successfully";
                                pwh.setWaitValue(false);
                                markDoneButton(idChat, pwh.getTypeOfRates(), rh);
                            }

                            if (pwh.isSetRates()) {
                                answer = (isRus) ? "Начальные показания воды и тарифы заданы успешно"
                                        : "Primary indications and rate for water set successfully!";

                                try {
                                    dataBaseHelper
                                            .insertPrimaryCounters(
                                                    objectMapper
                                                            .writeValueAsString(pwh),
                                                    "water", idChat, owner);
                                } catch (IOException e) {
                                    log.error(e.getMessage(), e);
                                }

                                defaultPrimaryButtons(rh, isRus);
                                chatObjectMapper.remove(idChat);
                                isRemoveCommand = true;
                            }
                        } else {

                            Integer countCounters = null;

                            try {
                                countCounters = Integer.valueOf(text);
                                if (countCounters < 0) {
                                    return (isRus) ? "Количество счетчиков горячей воды не может быть отрицательным (минимум 0)"
                                            : "Number of counters can not be negative";
                                }
                                if (countCounters == 0) {

                                    String hot = (isRus) ? "горячая" : "hot";
                                    String cold = (isRus) ? "холодная" : "cold";

                                    switch (pwh.getTypeOfWater()) {
                                        case "hot":
                                        case "горячая":
                                            pwh.setHotWater();
                                            pwh.setCountHotWaterCounter(0);
                                            hot = getEmoji("E29C85") + " " + hot;
                                            break;
                                        case "cold":
                                        case "холодная":
                                            return (isRus) ? "Счетчик холодной воды обязателен"
                                                    : "counter of cold water must be one minimum";
                                        default:
                                            break;
                                    }
                                    List<List<String>> buttons = new ArrayList<>();
                                    buttons.add(getButtonsList(hot, cold));
                                    buttons.add(getButtonsList((isRus) ? "главное меню"
                                            : "main menu"));

                                    rh.setNeedReplyMarkup(true);
                                    try {
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));
                                    } catch (JsonProcessingException e) {
                                        log.error(e.getMessage(), e);
                                    }

                                    pwh.setWaterSet(false);
                                    pwh.setTypeOfWater(null);

                                    answer = (isRus) ? "Показания горячей воды заданы успешно"
                                            : "Indications for hot water set successfully";

                                    // check finish set
                                    if (pwh.isSetWaterIndications()
                                            && !pwh.isWaitValue()) {
                                        buttons = new ArrayList<>();
                                        List<String> names = new ArrayList<>();
                                        String[] temp = {};

                                        if (pwh.getCountHotWaterCounter() > 0) {
                                            names.add((isRus) ? "горячая" : "hot");
                                        } else {
                                            pwh.setHotWaterRate(0.0);
                                        }

                                        if (pwh.getCountColdWaterCounter() > 0) {
                                            names.add((isRus) ? "холодная" : "cold");
                                        } else {
                                            pwh.setColdWaterRate(0.0);
                                        }

                                        names.add((isRus) ? "водоотведение"
                                                : "outfall rate");

                                        buttons.add(getButtonsList(names
                                                .toArray(temp)));
                                        buttons.add(getButtonsList((isRus) ? "главное меню"
                                                : "main menu"));

                                        cacheButtons.put(idChat, buttons);

                                        rh.setNeedReplyMarkup(true);
                                        try {
                                            rh.setReplyMarkup(objectMapper
                                                    .writeValueAsString(getButtons(buttons)));
                                        } catch (JsonProcessingException e) {

                                            log.error(e.getMessage(), e);
                                        }

                                        answer = (isRus) ? "Показания горячей и холодной воды заданы. Теперь необходимо задать тарифы"
                                                : "Indications for cold and hot water set successfully! Set rates for it";
                                        pwh.setRatesSet(true);
                                        pwh.setTypeOfWater("rate");
                                    }

                                    return answer;
                                }

                                if (countCounters > 5) {
                                    countCounters = 5;
                                }

                                StringBuilder sb = (isRus) ? new StringBuilder(
                                        "У вас ").append(countCounters).append(
                                        " счетчика.") : new StringBuilder(
                                        "Ok. You have ").append(countCounters)
                                        .append(" counter of ")
                                        .append(pwh.getTypeOfWater())
                                        .append(" water. ");

                                if (countCounters > 1) {
                                    List<List<String>> buttons = new ArrayList<>();

                                    for (int i = 0; i < countCounters; i++) {
                                        buttons.add(getButtonsList(String
                                                .valueOf(i + 1)));
                                    }

                                    if (isRus) {
                                        buttons.add(getButtonsList("назад"));
                                        buttons.add(getButtonsList("главное меню"));
                                    } else {
                                        buttons.add(getButtonsList("back"));
                                        buttons.add(getButtonsList("main menu"));
                                    }

                                    cacheButtons.put(idChat, buttons);

                                    rh.setNeedReplyMarkup(true);
                                    rh.setReplyMarkup(objectMapper
                                            .writeValueAsString(getButtons(buttons)));

                                    answer = sb
                                            .append((isRus) ? " Используйте кнопки ниже для задания значений"
                                                    : "Please use buttons below to set value")
                                            .toString();
                                } else {
                                    hideKeybord(rh);
                                    answer = (isRus) ? "Задайте начальное значение"
                                            : sb.append("Please set value for it")
                                            .toString();
                                }

                            } catch (NumberFormatException
                                    | JsonProcessingException e) {
                                log.error(e.getMessage(), e);
                                return NaN(isRus);
                            }

                            switch (typeOfWater) {
                                case "hot":
                                case "горячая":
                                    pwh.setCountHotWaterCounter(countCounters);
                                    break;
                                case "cold":
                                case "холодная":
                                    pwh.setCountColdWaterCounter(countCounters);
                                    break;
                                default:
                                    break;
                            }

                            pwh.setWaterSet(true);
                        }
                    }
                }
            }
            break;
            case "/setprimaryrentamount": {
                try {
                    Double rentAmount = Double.valueOf(text);
                    if (dataBaseHelper.insertPrimaryCounters(
                            rentAmount, "rent_amount", idChat, owner)) {
                        answer = (isRus) ? "Сумма аренды задана успешно"
                                : "Rent amount set successful";
                    }
                    defaultPrimaryButtons(rh, isRus);
                } catch (NumberFormatException e) {
                    return NaN(isRus);
                }
            }
            break;
            case "/setprimarylight": {
                isRemoveCommand = false;

                PrimaryLightHolder lightObj = (PrimaryLightHolder) chatObjectMapper
                        .get(idChat);
                if (null == lightObj.getTariffType()) {
                    try {
                        lightObj.setTariffType(Integer.valueOf(text));
                    } catch (NumberFormatException e) {
                        return (isRus) ? "Не верный тип счетчика. Используйте кнопки ниже"
                                : "Bad type of counter! Use buttons below";
                    }
                    switch (text) {
                        case "1":
                            hideKeybord(rh);
                            answer = (isRus) ? "У вас однотарифный счетчик. Задайте значение"
                                    : "Ok. You have one-tariff counter. Send simple message with value";
                            break;
                        case "2": {
                            List<List<String>> buttons = new ArrayList<>();

                            if (isRus) {
                                buttons.add(getButtonsList(
                                        PrimaryLightHolder.PeriodsRus.ДЕНЬ.name()
                                                .toLowerCase(),
                                        PrimaryLightHolder.PeriodsRus.НОЧЬ.name()
                                                .toLowerCase()));
                                buttons.add(getButtonsList("главное меню"));
                            } else {
                                buttons.add(getButtonsList(
                                        PrimaryLightHolder.Periods.DAY.name()
                                                .toLowerCase(),
                                        PrimaryLightHolder.Periods.NIGHT.name()
                                                .toLowerCase()));
                                buttons.add(getButtonsList("main menu"));
                            }

                            cacheButtons.put(idChat, buttons);

                            try {
                                rh.setNeedReplyMarkup(true);
                                rh.setReplyMarkup(objectMapper
                                        .writeValueAsString(getButtons(buttons)));
                            } catch (JsonProcessingException e) {
                                log.error(e.getMessage(), e);
                            }
                            answer = (isRus) ? "У вас двухтарифный счетчик. Выберите период для задания начального значения"
                                    : "Ok. You have two-tariff counter. Set primary value for it. Please use button below";
                        }
                        break;
                        case "3": {
                            List<List<String>> buttons = new ArrayList<>();
                            Set<String> periodsName = new TreeSet<>();
                            if (isRus) {
                                Arrays.asList(PeriodsRus.values())
                                        .forEach(
                                                e -> {
                                                    if (!e.equals(PrimaryLightHolder.PeriodsRus.ДЕНЬ)) {
                                                        periodsName.add(e.name()
                                                                .toLowerCase());
                                                    }
                                                });
                            } else {
                                Arrays.asList(Periods.values())
                                        .forEach(
                                                e -> {
                                                    if (!e.equals(PrimaryLightHolder.Periods.DAY)) {
                                                        periodsName.add(e.name()
                                                                .toLowerCase());
                                                    }
                                                });
                            }
                            String[] names = {};
                            buttons.add(getButtonsList(periodsName.toArray(names)));
                            buttons.add(getButtonsList((isRus) ? "главное меню"
                                    : "main menu"));

                            cacheButtons.put(idChat, buttons);

                            try {
                                rh.setNeedReplyMarkup(true);
                                rh.setReplyMarkup(objectMapper
                                        .writeValueAsString(getButtons(buttons)));
                            } catch (JsonProcessingException e) {
                                log.error(e.getMessage(), e);
                            }
                            answer = (isRus) ? "У вас трехтарифный счетчик. Выберите период для задания начального значения"
                                    : "Ok. You have three-tariff counter. Set primary value for it. Please use button below";
                            break;
                        }
                        default:
                            lightObj.setTariffType(null);
                            return (isRus) ? "Не верный тип счетчика. Используйте кнопки ниже"
                                    : "Bad type of counter! Use buttons below";

                    }
                } else {

                    Integer tariffType = lightObj.getTariffType();
                    Map<String, Double> rates = lightObj.getRates();

                    if (tariffType > 1) {
                        List<String> periodsStr = new ArrayList<>();

                        if (isRus) {
                            PeriodsRus[] periods = PrimaryLightHolder.PeriodsRus
                                    .values();
                            for (PeriodsRus period : periods) {
                                periodsStr.add(period.name().toLowerCase());
                            }
                        } else {
                            Periods[] periods = PrimaryLightHolder.Periods.values();
                            for (Periods period : periods) {
                                periodsStr.add(period.name().toLowerCase());
                            }
                        }

                        if (periodsStr.contains(text)) {
                            lightObj.setPeriod(text);
                            hideKeybord(rh);
                            if (isRus) {
                                return (lightObj.getRates() == null) ? "Задайте начальное значение для периода " +
                                        text
                                        : "Задайте значение тарифа для периода " +
                                        text;
                            } else {
                                return (lightObj.getRates() == null) ? "Ok. Set start indication value for " +
                                        text + " period"
                                        : "Ok. Set rate value for " +
                                        text + " period";
                            }
                        }
                    }
                    if (null != tariffType && rates == null
                            && (null != lightObj.getPeriod() || tariffType == 1)) {

                        switch (lightObj.getTariffType()) {
                            case 1:
                                try {
                                    hideKeybord(rh);
                                    lightObj.getIndications().put("t1",
                                            Double.valueOf(text));
                                    answer = (isRus) ? "Начальные показания электороэнергии заданы успешно. Теперь необходимо задать тариф"
                                            : "Primary start indications set successfully! Please set rate via simple send message with value.";
                                    lightObj.initRates();
                                } catch (NumberFormatException e) {
                                    log.error(e.getMessage(), e);
                                    return NaN(isRus);
                                }
                                break;
                            case 2:
                            case 3:
                                try {
                                    lightObj.getIndications().put(lightObj.getPeriod(),
                                            Double.valueOf(text));
                                    answer = (isRus) ? "Показание для периода " +
                                            lightObj.getPeriod() +
                                            " заданы успешно"
                                            : "Start indication for " +
                                            lightObj.getPeriod() +
                                            " period set succefully";

                                    markDoneButton(idChat, lightObj.getPeriod(), rh);

                                } catch (NumberFormatException e) {
                                    log.error(e.getMessage(), e);
                                    return NaN(isRus);
                                }

                                if (lightObj.isSetIndications()) {
                                    List<List<String>> buttons = new ArrayList<>();

                                    if (lightObj.getTariffType() == 2) {
                                        if (isRus) {
                                            buttons.add(getButtonsList(
                                                    PrimaryLightHolder.PeriodsRus.ДЕНЬ
                                                            .name().toLowerCase(),
                                                    PrimaryLightHolder.PeriodsRus.НОЧЬ
                                                            .name().toLowerCase()));
                                        } else {
                                            buttons.add(getButtonsList(
                                                    PrimaryLightHolder.Periods.DAY
                                                            .name().toLowerCase(),
                                                    PrimaryLightHolder.Periods.NIGHT
                                                            .name().toLowerCase()));
                                        }
                                    } else {
                                        Set<String> periodsName = new TreeSet<>();
                                        if (isRus) {
                                            Arrays.asList(
                                                    PeriodsRus
                                                            .values())
                                                    .forEach(
                                                            e -> {
                                                                if (!e.equals(PrimaryLightHolder.PeriodsRus.ДЕНЬ)) {
                                                                    periodsName
                                                                            .add(e.name()
                                                                                    .toLowerCase());
                                                                }
                                                            });
                                        } else {
                                            Arrays.asList(
                                                    Periods.values())
                                                    .forEach(
                                                            e -> {
                                                                if (!e.equals(PrimaryLightHolder.Periods.DAY)) {
                                                                    periodsName
                                                                            .add(e.name()
                                                                                    .toLowerCase());
                                                                }
                                                            });
                                        }
                                        String[] names = {};
                                        buttons.add(getButtonsList(periodsName
                                                .toArray(names)));
                                    }

                                    buttons.add(getButtonsList((isRus) ? "главное меню"
                                            : "main menu"));

                                    try {
                                        rh.setNeedReplyMarkup(true);
                                        rh.setReplyMarkup(objectMapper
                                                .writeValueAsString(getButtons(buttons)));
                                    } catch (JsonProcessingException e) {
                                        log.error(e.getMessage(), e);
                                    }

                                    answer = (isRus) ? "Начальные показания электроэнергии заданы успешно. Теперь необходимо задать тарифы для периодов"
                                            : "Primary start indications set successfully! Please set rates for it";
                                    lightObj.initRates();
                                    lightObj.setPeriod(null);
                                    cacheButtons.put(idChat, buttons);
                                }
                                break;

                            default:
                                break;
                        }
                    } else if (null != rates
                            && (null != lightObj.getPeriod() || tariffType == 1)) {

                        switch (lightObj.getTariffType()) {
                            case 1:
                                try {
                                    lightObj.getRates().put("t1", Double.valueOf(text));
                                    answer = (isRus) ? "Начальный показания и тарифы электроэнергии заданы успешно"
                                            : "Primary indications and rate for light set successfully!";
                                    cacheButtons.remove(idChat);

                                    try {
                                        dataBaseHelper
                                                .insertPrimaryCounters(
                                                        objectMapper
                                                                .writeValueAsString(lightObj),
                                                        "light", idChat, owner);
                                    } catch (JsonProcessingException e) {
                                        log.error(e.getMessage(), e);
                                    }

                                    defaultPrimaryButtons(rh, isRus);
                                    chatObjectMapper.remove(idChat);
                                    isRemoveCommand = true;
                                } catch (NumberFormatException e) {
                                    log.error(e.getMessage(), e);
                                    return NaN(isRus);
                                }
                                break;
                            case 2:
                            case 3:
                                try {
                                    lightObj.getRates().put(lightObj.getPeriod(),
                                            Double.valueOf(text));
                                    answer = (isRus) ? "Тариф задан успешно"
                                            : "Rate for " +
                                            lightObj.getPeriod() +
                                            " period set succefully";

                                    markDoneButton(idChat, lightObj.getPeriod(), rh);

                                } catch (NumberFormatException e) {
                                    log.error(e.getMessage(), e);
                                    return NaN(isRus);
                                }

                                if (lightObj.isSetRates()) {
                                    answer = (isRus) ? "Начальный показания и тарифы электроэнергии заданы успешно"
                                            : "Primary indications and rate for light set successfully!";

                                    try {
                                        dataBaseHelper
                                                .insertPrimaryCounters(
                                                        objectMapper
                                                                .writeValueAsString(lightObj),
                                                        "light", idChat, owner);
                                    } catch (IOException e) {
                                        log.error(e.getMessage(), e);
                                    }

                                    defaultPrimaryButtons(rh, isRus);
                                    chatObjectMapper.remove(idChat);
                                    isRemoveCommand = true;
                                }
                                break;

                            default:
                                break;
                        }

                    } else {
                        return (isRus) ? "Выбран не верный период. Используйте кнопки ниже"
                                : "Wrong period! Use buttons below";
                    }
                }
            }
            break;
            default:
                return (isRus) ? "Я не могу ответить на этот вопрос"
                        : "I can't answer at this question";
        }

        if (isRemoveCommand) {
            activeCommand.remove(idChat);
        }

        return answer;
    }

    private ReplyKeyboardMarkup getButtons(List<List<String>> buttonNames) {

        List<List<KeyboardButton>> keyButtons = new ArrayList<>();

        ReplyKeyboardMarkup rkm = new ReplyKeyboardMarkup();
        rkm.setResize_keyboard(true);
        rkm.setOne_time_keyboard(false);
        rkm.setKeyboard(keyButtons);

        buttonNames.forEach(name -> {
            List<KeyboardButton> buttons = new ArrayList<>();
            name.forEach(e -> {
                KeyboardButton key = new KeyboardButton();
                key.setText(e);
                buttons.add(key);
            });

            keyButtons.add(buttons);
        });
        return rkm;
    }

    private void setDefaultRentButtons(ResponseHolder rh, boolean isRus) {

        List<List<String>> buttons = new ArrayList<>();
        buttons.add(getButtonsList((isRus) ? "главное меню" : "main menu"));

        try {
            rh.setNeedReplyMarkup(true);
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(getButtons(buttons)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void defaultPrimaryButtons(ResponseHolder rh, boolean isRus) {

        List<List<String>> buttons = new ArrayList<>();
        Optional<Document> document = Optional.ofNullable(dataBaseHelper.getFirstDocByFilter("rent_const",
                Filters.eq("id_chat", rh.getChatId())));

        String light = null;
        String water = null;
        String rentAmount = null;

        if (isRus) {
            light = "свет";
            water = "вода";
            rentAmount = "арендная плата";
        } else {
            light = "set light";
            water = "set water";
            rentAmount = "set rent amount";
        }

        if (document.isPresent()) {
            if (null != document.get().get("light")) {
                light = getEmoji("E29C85") + " " + light;
            }

            if (null != document.get().get("water")) {
                water = getEmoji("E29C85") + " " + water;
            }

            if (null != document.get().get("rent_amount")) {
                rentAmount = getEmoji("E29C85") + " " + rentAmount;
            }
        }

        buttons.add(getButtonsList(water, rentAmount, light));

        buttons.add(getButtonsList((isRus) ? "главное меню" : "main menu"));

        try {
            rh.setNeedReplyMarkup(true);
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(getButtons(buttons)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void defaultAddMonthButtons(RentHolder rent, ResponseHolder rh,
                                        boolean isRus) {
        List<List<String>> buttons = new ArrayList<>();

        if (isRus) {
            buttons.add(getButtonsList(
                    (null == rent.getMonthOfRent()) ? "месяц"
                            : getEmoji("E29C85") + " месяц",
                    (rent.isLightSet()) ? getEmoji("E29C85")
                            + " показания света" : "показания света"));
            buttons.add(getButtonsList(rent.isWaterSet() ? getEmoji("E29C85")
                            + " показания воды" : "показания воды",
                    (null == rent.getTakeout()) ? "вычет" : getEmoji("E29C85")
                            + " вычет"));
            buttons.add(getButtonsList("инфо", "рассчитать"));
            buttons.add(getButtonsList("главное меню"));
        } else {
            buttons.add(getButtonsList(
                    (null == rent.getMonthOfRent()) ? "name of month"
                            : getEmoji("E29C85") + " name of month", (rent
                            .isLightSet()) ? getEmoji("E29C85") + " light"
                            : "light"));
            buttons.add(getButtonsList(rent.isWaterSet() ? getEmoji("E29C85")
                            + " water" : "water",
                    (null == rent.getTakeout()) ? "takeout"
                            : getEmoji("E29C85") + " takeout"));
            buttons.add(getButtonsList("current statistic", "calc"));
            buttons.add(getButtonsList("main menu"));
        }

        rh.setNeedReplyMarkup(true);
        try {
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(getButtons(buttons)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void hideKeybord(ResponseHolder rh) {
        rh.setNeedReplyMarkup(true);
        try {
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(new ReplyKeyboardHide()));
        } catch (JsonProcessingException e) {

            log.error(e.getMessage());
        }
    }

    private void defaultWaterButtons(RentHolder rent, ResponseHolder rh,
                                     String fillCounter) {
        rh.setNeedReplyMarkup(true);
        try {
            List<List<String>> list = rent.getWaterButtons();
            if (null != fillCounter) {

                list.forEach(e -> {
                    int index = e.indexOf(fillCounter);
                    if (index != -1) {
                        String done = e.get(index);
                        e.remove(index);
                        e.add(index, getEmoji("E29C85") + " " + done);
                    }
                });
            }
            rh.setReplyMarkup(objectMapper.writeValueAsString(getButtons(list)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    private List<String> getButtonsList(String... buttonsNames) {

        List<String> buttons = new ArrayList<>();
        int len = buttonsNames.length;

        for (int i = 0; i < len; i++) {
            buttons.add(buttonsNames[i]);
        }
        return buttons;
    }

    private void markDoneButton(String idChat, String buttonName,
                                ResponseHolder rh) {
        ListIterator<String> iter = cacheButtons.get(idChat).get(0)
                .listIterator();

        while (iter.hasNext()) {
            String e = iter.next();
            if (e.equalsIgnoreCase(buttonName)) {
                iter.set(e.replace(e, getEmoji("E29C85") + " " + e));
            }
        }

        try {
            rh.setNeedReplyMarkup(true);
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(getButtons(cacheButtons.get(idChat))));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    private String NaN(boolean isRus) {
        return (isRus) ? "Неверный формат! Значение должно быть числом. Попробуйте снова"
                : "Wrong format! Value must be a number! Try again";
    }

    private String getStart(ResponseHolder rh) {

        List<List<String>> buttons = new ArrayList<>();
        buttons.add(getButtonsList("rent", "аренда"));

        String answer = "For english tap rent:\n" +
                "<b>rent</b> (/rent) - calculating rent\n\n" +
                "Для продолжения на русском языке используйте команду:\n\n" +
                "<b>аренда</b> - расчет арендной платы за месяц\n";

        rh.setNeedReplyMarkup(true);
        try {
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(getButtons(buttons)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);

        }
        return answer;
    }

    private String getRentMenu(String id, boolean isRus, ResponseHolder rh) {

        String answer = null;

        cacheButtons.remove(id);
        List<List<String>> buttons = new ArrayList<>();

        if (dataBaseHelper.existRentUser(id)) {

            if (dataBaseHelper.existPayment(id)) {
                if (isRus) {
                    buttons.add(getButtonsList("добавить месяц"));
                    buttons.add(getButtonsList("статистика", "платежи"));
                    buttons.add(getButtonsList("тарифы", "изменить тарифы"));
                    buttons.add(getButtonsList("удалить платеж", "удалить все"));
                    buttons.add(getButtonsList("изменить начальные показания"));
                    answer = "Вы можете использовать следующий команды:\n\n" +
                            "<b>добавить месяц</b> - новый месяц аренды\n" +
                            "<b>статистика</b> - статистика по месяцам\n" +
                            "<b>платежи</b> - история платежей\n" +
                            "<b>тарифы</b> - заданные тарифы\n" +
                            "<b>изменить тарифы</b> - изменить заданные тарифы\n" +
                            "<b>удалить платеж</b> - удалить платеж за месяц\n" +
                            "<b>удалить все</b> - удалить все данные об аренде\n" +
                            "<b>изменить начальные показания</b> - изменить начальные показания счетчиков или стоимость аренды";
                } else {
                    buttons.add(getButtonsList("add month"));
                    buttons.add(getButtonsList("details", "payments"));
                    buttons.add(getButtonsList("rates", "change rates"));
                    buttons.add(getButtonsList("remove payment", "remove rent"));
                    buttons.add(getButtonsList("change primary"));
                    answer = "You can control your rent by sending these commands (Use buttons below):\n\n" +
                            "add month (/rent_add) - add month of rent\n" +
                            "details (/getstatbymonth) - getting rent statistics by month\n" +
                            "payments (/gethistory) - return total amount by months\n" +
                            "rates (/getrates) - return all rates for rent\n" +
                            "change rates (/changerates) - change rates for rent\n" +
                            "remove payment (/delmonthstat) - remove statistics by month\n" +
                            "remove rent (/purge) - remove statistics for all months of rent and primary values\n" +
                            "change primary (/setprimarycounters)- edit starting indications\n";
                }
            } else {
                if (isRus) {
                    buttons.add(getButtonsList("добавить месяц"));
                    buttons.add(getButtonsList("тарифы", "изменить тарифы"));
                    buttons.add(getButtonsList("изменить начальные показания"));
                    answer = "Вы можете использовать следующие команды:\n\n" +
                            "<b>добавить месяц</b> - новый месяц аренды\n" +
                            "<b>тарифы</b> - просмотр тарифов\n" +
                            "<b>изменить тарифы</b>- изменить тарифы\n" +
                            "<b>изменить начальные показания</b> - изменить начальные показания счетчиков или стоимость аренды";
                } else {
                    buttons.add(getButtonsList("add month"));
                    buttons.add(getButtonsList("rates", "change rates"));
                    buttons.add(getButtonsList("change primary"));
                    answer = "You can control your rent by sending these commands (Use buttons below):\n\n" +
                            "add month (/rent_add) - add month of rent\n" +
                            "rates (/getrates) - return all rates for rent\n" +
                            "change rates (/changerates) - change rates for rent\n" +
                            "change primary (/setprimarycounters)- edit starting indications";
                }
            }

        } else {
            if (isRus) {

                buttons.add(getButtonsList("начальные показания"));

                answer = "Задайте начальные показания для доступа ко всем функциям.\n\nИспользуйте команду:\n\n" +
                        "<b>начальные показания</b> - задать начальные показания счетчиков и стоимость аренды";
            } else {
                buttons.add(getButtonsList("new primary"));

                answer = "For access to all functions for control your rent you must set primary counters. Please use this command:\n" +
                        "new primary (/setprimarycounters)- set starting indications";
            }
        }

        rh.setNeedReplyMarkup(true);
        try {
            rh.setReplyMarkup(objectMapper
                    .writeValueAsString(getButtons(buttons)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }

        rentData.remove(id);

        return answer;
    }
}
