package ru.max.bot.telegram_api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * This object represents one button of the reply keyboard. For simple text
 * buttons String can be used instead of this object to specify text of the
 * button. Optional fields are mutually exclusive.
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
public class KeyboardButton {

    /**
     * Text of the button. If none of the optional fields are used, it will be
     * sent to the bot as a message when the button is pressed
     */
    private String text;

    /**
     * Optional. If True, the user's phone number will be sent as a contact when
     * the button is pressed. Available in private chats only
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean request_contact;

    /**
     * Optional. If True, the user's current location will be sent when the
     * button is pressed. Available in private chats only
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean request_location;

    public KeyboardButton() {
    }

}
