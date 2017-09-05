package org.alexdev.icarus.game.room;

import java.util.HashMap;
import java.util.LinkedList;

import org.alexdev.icarus.dao.mysql.RoomDao;
import org.alexdev.icarus.game.commands.CommandManager;
import org.alexdev.icarus.game.entity.Entity;
import org.alexdev.icarus.game.entity.EntityType;
import org.alexdev.icarus.game.furniture.interactions.Interaction;
import org.alexdev.icarus.game.item.Item;
import org.alexdev.icarus.game.messenger.PlayerMessage;
import org.alexdev.icarus.game.pathfinder.Pathfinder;
import org.alexdev.icarus.game.pathfinder.Position;
import org.alexdev.icarus.game.player.Player;
import org.alexdev.icarus.game.plugins.PluginEvent;
import org.alexdev.icarus.game.plugins.PluginManager;
import org.alexdev.icarus.game.room.model.RoomTile;
import org.alexdev.icarus.game.room.model.Rotation;
import org.alexdev.icarus.log.DateTime;
import org.alexdev.icarus.messages.outgoing.room.notify.FloodFilterMessageComposer;
import org.alexdev.icarus.messages.outgoing.room.user.CarryObjectComposer;
import org.alexdev.icarus.messages.outgoing.room.user.DanceMessageComposer;
import org.alexdev.icarus.messages.outgoing.room.user.TalkMessageComposer;
import org.alexdev.icarus.messages.outgoing.room.user.UserStatusMessageComposer;
import org.alexdev.icarus.messages.parsers.MessageComposer;
import org.alexdev.icarus.util.GameSettings;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RoomUser {

	private int virtualId;
	private int lastChatId;
	private int danceId;
	private int roomRequestedId;

	private Position position;
	private Position goal;
	private Position next;

	private boolean isWalking;
	private boolean needsUpdate;
	private boolean isTeleporting;
	private int teleportRoomId;

	private long chatFloodTimer;
	private int chatCount;
	private int lookResetTime;

	private HashMap<String, String> statuses;
	private LinkedList<Position> path;
	private Entity entity;
	private Room room;
	private Item currentItem;
	private int carryTimer;
	private int carryItem;
	private boolean isRolling;
	private boolean isWalkingAllowed;

	public RoomUser(Entity entity) {
		this.dispose();
		this.entity = entity;
	}

	public void stopWalking() {

		this.removeStatus("mv");

		this.next = null;
		this.isWalking = false;

		this.updateCurrentItem();

		if (this.entity.getType() == EntityType.PLAYER) {

			PluginManager.callEvent(PluginEvent.ROOM_STOP_WALKING_EVENT, new LuaValue[] {  
					CoerceJavaToLua.coerce((Player)this.entity), 
					CoerceJavaToLua.coerce(this.room)
			});
		}

		this.needsUpdate = true;
	}

	public boolean updateCurrentItem() {

		Item item = this.room.getMapping().getHighestItem(this.position.getX(), this.position.getY());

		boolean no_current_item = false;

		if (item != null) {
			if (item.canWalk()) {
				this.currentItem = item;
				this.triggerCurrentItem();
			} else {
				no_current_item = true;
			}
		} else {
			no_current_item = true;
		}

		if (no_current_item) {
			this.currentItem = null;
			return false;
		}

		return true;
	}

	public void triggerCurrentItem() {

		if (this.currentItem == null) {
			this.removeStatus("sit");
			this.removeStatus("lay");
		} else {
			Interaction handler = this.currentItem.getDefinition().getInteractionType().getHandler();

			if (handler != null) {
				handler.onStopWalking(this.currentItem, this);
			}
		}

		this.position.setZ(this.room.getMapping().getTile(this.position.getX(), this.position.getY()).getHeight());
		this.needsUpdate = true;
	}

	public boolean containsStatus(String key) {
		return this.statuses.containsKey(key);
	}

	public void removeStatus(String key) {
		this.statuses.remove(key);
	}


	public void setStatus(String key, String value, boolean infinite, int duration) {

		if (this.containsStatus(key)) {
			this.removeStatus(key);
		}

		this.statuses.put(key, value);
	}

	public void chat(String message) {
		this.chat(message, this.lastChatId, 1, false, false, false);
	}

	public void shout(String message) {
		this.chat(message, this.lastChatId, 1, true, false, false);
	}

	public void chatSelf(String message) {
		this.chat(message, this.lastChatId, 1, false, false, true);
	}

	public void shoutSelf(String message) {
		this.chat(message, this.lastChatId, 1, true, false, true);
	}

	public void chat(String message, int bubble, int count, boolean shout, boolean spamCheck, boolean self) {

		if (this.entity.getType() != EntityType.PLAYER) {

			MessageComposer composer = null;

			if (this.entity.getType() == EntityType.BOT) {
				composer = new TalkMessageComposer(this, shout, message, 2, bubble);
			}
			
			this.room.send(composer);

			return;
		}

		Player player = (Player)this.entity;
		boolean isStaff = player.getDetails().hasFuse("moderator");

		if (spamCheck && !self) {
			if (DateTime.getTimeSeconds() < this.chatFloodTimer && this.chatCount >= GameSettings.MAX_CHAT_BEFORE_FLOOD) {

				if (!isStaff) {
					player.send(new FloodFilterMessageComposer(GameSettings.CHAT_FLOOD_WAIT));
					return;
				}
			}
		}

		if (bubble == 2 || (bubble == 23 && !player.getDetails().hasFuse("moderator")) || bubble < 0 || bubble > 29) {
			bubble = this.lastChatId;
		}

		RoomDao.saveChatlog(player, this.room.getData().getId(), shout ? "SHOUT" : "CHAT", message);

		if (CommandManager.hasCommand(message)) {
			CommandManager.invokeCommand(player, message);
			return;
		} 

		// Plugin event handle for chat
		PlayerMessage playerMessage = new PlayerMessage(this.entity.getDetails().getId(), -1, message);	{

			PluginEvent event = shout ? PluginEvent.ROOM_PLAYER_SHOUT_EVENT : PluginEvent.ROOM_PLAYER_CHAT_EVENT;

			PluginManager.callEvent(event, new LuaValue[] {  
					CoerceJavaToLua.coerce(player),
					CoerceJavaToLua.coerce(this.room),
					CoerceJavaToLua.coerce(playerMessage) 
			});

			message = playerMessage.getMessage();
		}
		// End plugin event handle

		MessageComposer composer = new TalkMessageComposer(this, shout, message, this.lastChatId, bubble);
		this.lastChatId = bubble;

		if (self) {
			player.send(composer);
		} else {
			this.room.send(composer);
		}

		for (Player person : this.room.getPlayers()) {

			if (this.entity == person) {
				continue;
			}

			person.getRoomUser().lookTowards(this.entity.getRoomUser().getPosition());
		}

		if (spamCheck && !self) {
			if (!player.getDetails().hasFuse("moderator")) {

				if (DateTime.getTimeSeconds() > this.chatFloodTimer && this.chatCount >= GameSettings.MAX_CHAT_BEFORE_FLOOD) {
					this.chatCount = 0;
				} else {
					this.chatCount = this.chatCount + 1;
				}

				this.chatFloodTimer = (DateTime.getTimeSeconds() + GameSettings.CHAT_FLOOD_SECONDS);

			}
		}
	}

	public void lookTowards(Position look) {

		if (this.isWalking) {
			return;
		}

		int diff = this.getPosition().getRotation() - Rotation.calculate(this.position.getX(), this.position.getY(), look.getX(), look.getY());

		if ((this.getPosition().getRotation() % 2) == 0) {

			if (diff > 0) {
				this.position.setHeadRotation(this.getPosition().getRotation() - 1);
			} else if (diff < 0) {
				this.position.setHeadRotation(this.getPosition().getRotation() + 1);
			} else {
				this.position.setHeadRotation(this.getPosition().getRotation());
			}
		}

		this.lookResetTime = 6;
		this.needsUpdate = true;
	}

	public void warpTo(int x, int y, int rotation) {

		if (this.room.getModel().invalidXYCoords(x, y)) {
			return;
		}

		// remove entity from previous tile
		this.room.getMapping().getTile(this.position.getX(), this.position.getY()).setEntity(null);

		this.position.setX(x);
		this.position.setY(y);
		this.position.setZ(this.room.getMapping().getTileHeight(x, y));
		this.position.setRotation(rotation);

		// set entity to new title
		this.room.getMapping().getTile(x, y).setEntity(entity);

		this.needsUpdate = true;
	}

	public void walkTo(int X, int Y) {

		if (this.room.getModel().invalidXYCoords(X, Y)) {
			return;
		}

		if (this.room.getModel().isBlocked(X, Y)) {
			return;
		}

		if (!this.room.getMapping().isTileWalkable(this.entity, X, Y)) {
			return;
		}

		if (this.position.isMatch(new Position(X, Y))) {
			return;
		}

		if (!this.isWalkingAllowed) {
			return;
		}

		this.goal.setX(X);
		this.goal.setY(Y);

		if (this.entity.getType() == EntityType.PLAYER) {

			PluginManager.callEvent(PluginEvent.ROOM_WALK_REQUEST_EVENT, new LuaValue[] {  
					CoerceJavaToLua.coerce((Player)this.entity),
					CoerceJavaToLua.coerce(this.room),
					CoerceJavaToLua.coerce(this.position), 
					CoerceJavaToLua.coerce(this.goal) 
			});
		}

		LinkedList<Position> path = Pathfinder.makePath(this.entity);

		if (path == null) {
			return;
		}

		if (path.size() == 0) {
			return;
		}

		this.path = path;
		this.isWalking = true;
	}

	public void carryItem(int vendingId) {

		if (vendingId == -1) {
			return;
		}

		this.carryTimer = 0;
		this.carryItem = vendingId;

		if (vendingId > 0)
			this.carryTimer = 240;
		else
			this.carryTimer = 0;

		this.room.send(new CarryObjectComposer(this.virtualId, vendingId)); 
	}

	public void dispose() {

		if (this.statuses != null) {
			this.statuses.clear();
		} else {
			this.statuses = Maps.newHashMap();
		}

		if (this.path != null) {
			this.path.clear();
		} else {
			this.path = Lists.newLinkedList();
		}

		this.position = null;
		this.goal = null;

		this.position = new Position(0, 0, 0);
		this.goal = new Position(0, 0, 0);

		this.lastChatId = 0;
		this.roomRequestedId = -1;
		this.virtualId = -1;
		this.danceId = 0;
		this.lookResetTime = -1;
		this.carryItem = 0;
		this.carryTimer = -1;

		this.needsUpdate = false;
		this.isRolling = false;
		this.isWalking = false;
		this.isWalkingAllowed = true;

	}
	
	public void startDancing(int danceId) {
		
		this.danceId = danceId;
		this.room.send(new DanceMessageComposer(this.virtualId, danceId));
	}
	
	public void stopDancing() {
		this.startDancing(0);
	}

	public Position getPosition() {
		return position;
	}

	public void setPosition(Position position) {
		this.position = position;
	}

	public Position getGoal() {
		return goal;
	}

	public void setGoal(Position goal) {
		this.goal = goal;
	}

	public Position getNext() {
		return next;
	}

	public void setNext(Position next) {
		this.next = next;
	}

	public void updateStatus() {
		this.room.send(new UserStatusMessageComposer(this.entity));
	}

	public boolean isDancing() {
		return this.danceId != 0;
	}
	public int getVirtualId() {
		return virtualId;
	}

	public void setVirtualId(int virtualId) {
		this.virtualId = virtualId;
	}

	public int getLastChatId() {
		return lastChatId;
	}

	public void setLastChatId(int lastChatId) {
		this.lastChatId = lastChatId;
	}

	public int getDanceId() {
		return danceId;
	}

	public void setDanceId(int danceId) {
		this.danceId = danceId;
	}

	public HashMap<String, String> getStatuses() {
		return statuses;
	}

	public LinkedList<Position> getPath() {
		return path;
	}

	public void setPath(LinkedList<Position> path) {

		if (this.path != null) {
			this.path.clear();
		}

		this.path = path;
	}

	public boolean needsUpdate() {
		return needsUpdate;
	}

	public void setNeedUpdate(boolean needsWalkUpdate) {
		this.needsUpdate = needsWalkUpdate;
	}

	public Room getRoom() {
		return room;
	}

	public int getRoomId() {
		return (room == null ? 0 : room.getData().getId());
	}

	public void setRoom(Room room) {
		this.room = room;
	}

	public boolean isWalking() {
		return isWalking;
	}

	public void setWalking(boolean isWalking) {
		this.isWalking = isWalking;
	}

	public Entity getEntity() {
		return entity;
	}

	public int getLookResetTime() {
		return lookResetTime;
	}

	public void setLookResetTime(int lookResetTime) {
		this.lookResetTime = lookResetTime;
	}

	public Item getCurrentItem() {
		return currentItem;
	}

	public void setCurrentItem(Item currentItem) {
		this.currentItem = currentItem;
	}

	public int getCarryTimer() {
		return carryTimer;
	}

	public void setCarryTimer(int carryTimer) {
		this.carryTimer = carryTimer;
	}

	public int getCarryItem() {
		return carryItem;
	}

	public void setCarryItem(int carryItem) {
		this.carryItem = carryItem;
	}

	public boolean isRolling() {
		return isRolling;
	}

	public void setRolling(boolean isRolling) {
		this.isRolling = isRolling;
	}

	public boolean isNeedsUpdate() {
		return needsUpdate;
	}

	public void setNeedsUpdate(boolean needsUpdate) {
		this.needsUpdate = needsUpdate;
	}

	public boolean isTeleporting() {
		return isTeleporting;
	}

	public void setTeleporting(boolean isTeleporting) {
		this.isTeleporting = isTeleporting;
	}

	public int getTeleportRoomId() {
		return teleportRoomId;
	}

	public void setTeleportRoomId(int teleportRoomId) {
		this.teleportRoomId = teleportRoomId;
	}

	public boolean isWalkingAllowed() {
		return isWalkingAllowed;
	}

	public void setWalkingAllowed(boolean isWalkingAllowed) {
		this.isWalkingAllowed = isWalkingAllowed;
	}

	public int getRequestedRoomId() {
		return roomRequestedId;
	}

	public void setRequestedRoomId(int roomRequestedId) {
		this.roomRequestedId = roomRequestedId;
	}


}
