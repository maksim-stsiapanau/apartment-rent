package ru.max.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.max.bot.database.DataBaseHelper;

@Configuration
@EnableScheduling
@Slf4j
public class BotConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public String telegramApiUrl(DataBaseHelper dataBaseHelper) {
        StringBuilder sb = new StringBuilder();

        dataBaseHelper.getToken().ifPresent(e -> {
            sb.append("https://api.telegram.org/bot").append(e).append("/");
        });

        String telegramApiUrl = sb.toString();

        if (telegramApiUrl.length() == 0) {
            log.error("Telegram api url doesn't set! Start is failed!");
            System.exit(0);

        }

        return telegramApiUrl;
    }
}
