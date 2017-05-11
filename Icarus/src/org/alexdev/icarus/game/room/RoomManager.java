package org.alexdev.icarus.game.room;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.alexdev.icarus.dao.mysql.RoomDao;
import org.alexdev.icarus.game.room.settings.RoomType;

import com.google.common.collect.Lists;

public class RoomManager {

    private static List<Room> loadedRooms;
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

    public static void load() {
        loadedRooms = Lists.newArrayList();
        scheduler = Executors.newScheduledThreadPool(8);
        
        RoomDao.getModels();
        RoomDao.getPublicRooms(true);
    }

	public static void add(Room room) {

		boolean add = true;
		
		for (Room loadedRoom : loadedRooms) {

			if (room.getData().getId() == loadedRoom.getData().getId()) {
				add = false;
			}
		}

		if (add) {
			loadedRooms.add(room);
		}
	}
	
	public static List<Room> getPublicRooms() {
		try {
			return loadedRooms.stream().filter(room -> room.getData().getRoomType() == RoomType.PUBLIC).collect(Collectors.toList());
		} catch (Exception e) {
			return null;
		}
	}
	
	public static List<Room> getPlayerRooms(int userId) {
		try {
			return loadedRooms.stream().filter(room -> room.getData().getOwnerId() == userId).collect(Collectors.toList());
		} catch (Exception e) {
			return null;
		}
	}

	public static Room find(int roomId) {

		try {
			return getLoadedRooms().stream().filter(r -> r.getData().getId() == roomId).findFirst().get();
		} catch (Exception e) {
			return null;
		}
	}

	public static List<Room> getLoadedRooms() {
		return loadedRooms;
	}

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }

}
