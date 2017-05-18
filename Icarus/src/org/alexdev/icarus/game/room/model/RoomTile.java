package org.alexdev.icarus.game.room.model;

import java.util.ArrayList;
import java.util.List;

import org.alexdev.icarus.game.entity.Entity;
import org.alexdev.icarus.game.item.Item;
import org.alexdev.icarus.game.room.Room;

public class RoomTile {

    private double height = 0;
    private boolean overrideLock = false;
    
    private List<Item> items;
    
    private Room room;
    private Item highestItem = null;
    private Item itemUnderneath = null;
    private Entity entity;
    
    public RoomTile(Room room) {
        this.room = room;
        this.items = new ArrayList<Item>();
        this.entity = null;
    }
    
    public Room getRoom() {
        return room;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
    
    public List<Item> getItems() {
        return items;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public Item getHighestItem() {
        return highestItem;
    }

    public void setHighestItem(Item highestItem) {
        this.highestItem = highestItem;
    }

    public boolean hasOverrideLock() {
        return overrideLock;
    }

    public void setOverrideLock(boolean overrideLock) {
        this.overrideLock = overrideLock;
    }

    public Item getItemUnderneath() {
        return itemUnderneath;
    }

    public void setItemUnderneath(Item itemUnderneath) {
        this.itemUnderneath = itemUnderneath;
    }
}
