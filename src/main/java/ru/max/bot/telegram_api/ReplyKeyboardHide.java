package ru.max.bot.telegram_api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class ReplyKeyboardHide {

	/**
	 * Requests clients to hide the custom keyboard
	 */
	private final Boolean hide_keyboard = true;

	/**
	 * Optional. Use this parameter if you want to hide keyboard for specific
	 * users only. Targets: 1) users that are @mentioned in the text of the
	 * Message object; 2) if the bot's message is a reply (has
	 * reply_to_message_id), sender of the original message.
	 * 
	 * Example: A user votes in a poll, bot returns confirmation message in
	 * reply to the vote and hides keyboard for that user, while still showing
	 * the keyboard with poll options to users who haven't voted yet.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Boolean selective;

	public ReplyKeyboardHide() {
		// TODO Auto-generated constructor stub
	}

}
