package org.alexdev.icarus.messages.incoming.room;

import org.alexdev.icarus.game.player.Player;
import org.alexdev.icarus.game.room.RoomAction;
import org.alexdev.icarus.messages.MessageEvent;
import org.alexdev.icarus.server.api.messages.ClientMessage;

public class LeaveRoomMessageEvent implements MessageEvent {

    @Override
    public void handle(Player player, ClientMessage request) {
        player.performRoomAction(RoomAction.LEAVE_ROOM, true);
    }
}
