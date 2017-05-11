package org.alexdev.icarus.messages.incoming.room;

import org.alexdev.icarus.game.player.Player;
import org.alexdev.icarus.game.room.Room;
import org.alexdev.icarus.game.room.RoomManager;
import org.alexdev.icarus.messages.MessageEvent;
import org.alexdev.icarus.server.messages.AbstractReader;

public class DoorbellEnterMessageEvent implements MessageEvent {

	@Override
	public void handle(Player player, AbstractReader request) {
		
		int roomId = request.readInt();
		
		Room room = RoomManager.find(roomId);
		
		if (room == null) {
			return;
		}
		
		room.loadRoom(player);
	}
}
