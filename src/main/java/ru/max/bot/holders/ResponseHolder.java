package ru.max.bot.holders;

import lombok.Data;

/**
 * Contains information about response message
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
public class ResponseHolder {

    private String responseMessage;
    private boolean needReplyMarkup;
    private String replyMarkup;
    private String chatId;


}
