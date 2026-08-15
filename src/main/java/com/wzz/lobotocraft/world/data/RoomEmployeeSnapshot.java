package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.wzz.lobotocraft.work.WorkType;

import java.util.UUID;

/** Compact, viewer-filtered AI employee data used by the facility monitor. */
public record RoomEmployeeSnapshot(
        UUID id, String name, String departmentId, ResourceLocation dimension, BlockPos position,
        float health, float maxHealth, float mental, float maxMental,
        int fortitude, int prudence, int temperance, int justice, int employeeLevel,
        String weaponName, String weaponNameKey, String armorName, String armorNameKey,
        String status, boolean commandable,
        boolean eligible, String unavailableReason, String unavailableReasonKey,
        float successRate, int estimatedSeconds,
        ResourceLocation workRoomDimension, BlockPos workRoomPosition,
        WorkType currentWorkType, int workSuccessBoxes, int workFailureBoxes, int workBoxCapacity) {

    private static final int MAX_NAME = 64;
    private static final int MAX_TEXT = 128;

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeUtf(name, MAX_NAME);
        buffer.writeUtf(departmentId, FacilityAreaEntry.MAX_DEPARTMENT_LENGTH);
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(position);
        buffer.writeFloat(health);
        buffer.writeFloat(maxHealth);
        buffer.writeFloat(mental);
        buffer.writeFloat(maxMental);
        buffer.writeVarInt(fortitude);
        buffer.writeVarInt(prudence);
        buffer.writeVarInt(temperance);
        buffer.writeVarInt(justice);
        buffer.writeVarInt(employeeLevel);
        buffer.writeUtf(weaponName, MAX_TEXT);
        buffer.writeUtf(weaponNameKey, MAX_TEXT);
        buffer.writeUtf(armorName, MAX_TEXT);
        buffer.writeUtf(armorNameKey, MAX_TEXT);
        buffer.writeUtf(status, MAX_TEXT);
        buffer.writeBoolean(commandable);
        buffer.writeBoolean(eligible);
        buffer.writeUtf(unavailableReason, MAX_TEXT);
        buffer.writeUtf(unavailableReasonKey, MAX_TEXT);
        buffer.writeFloat(successRate);
        buffer.writeVarInt(estimatedSeconds);
        boolean hasWorkRoom = workRoomDimension != null && workRoomPosition != null;
        buffer.writeBoolean(hasWorkRoom);
        if (hasWorkRoom) {
            buffer.writeResourceLocation(workRoomDimension);
            buffer.writeBlockPos(workRoomPosition);
        }
        buffer.writeBoolean(currentWorkType != null);
        if (currentWorkType != null) buffer.writeEnum(currentWorkType);
        buffer.writeVarInt(workSuccessBoxes);
        buffer.writeVarInt(workFailureBoxes);
        buffer.writeVarInt(workBoxCapacity);
    }

    public static RoomEmployeeSnapshot read(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(MAX_NAME);
        String departmentId = buffer.readUtf(FacilityAreaEntry.MAX_DEPARTMENT_LENGTH);
        ResourceLocation dimension = buffer.readResourceLocation();
        BlockPos position = buffer.readBlockPos();
        float health = buffer.readFloat();
        float maxHealth = buffer.readFloat();
        float mental = buffer.readFloat();
        float maxMental = buffer.readFloat();
        int fortitude = buffer.readVarInt();
        int prudence = buffer.readVarInt();
        int temperance = buffer.readVarInt();
        int justice = buffer.readVarInt();
        int employeeLevel = buffer.readVarInt();
        String weaponName = buffer.readUtf(MAX_TEXT);
        String weaponNameKey = buffer.readUtf(MAX_TEXT);
        String armorName = buffer.readUtf(MAX_TEXT);
        String armorNameKey = buffer.readUtf(MAX_TEXT);
        String status = buffer.readUtf(MAX_TEXT);
        boolean commandable = buffer.readBoolean();
        boolean eligible = buffer.readBoolean();
        String unavailableReason = buffer.readUtf(MAX_TEXT);
        String unavailableReasonKey = buffer.readUtf(MAX_TEXT);
        float successRate = buffer.readFloat();
        int estimatedSeconds = buffer.readVarInt();
        ResourceLocation workRoomDimension = null;
        BlockPos workRoomPosition = null;
        if (buffer.readBoolean()) {
            workRoomDimension = buffer.readResourceLocation();
            workRoomPosition = buffer.readBlockPos();
        }
        WorkType currentWorkType = buffer.readBoolean() ? buffer.readEnum(WorkType.class) : null;
        return new RoomEmployeeSnapshot(id, name,
                departmentId, dimension, position, health, maxHealth, mental, maxMental,
                fortitude, prudence, temperance, justice, employeeLevel,
                weaponName, weaponNameKey, armorName, armorNameKey,
                status, commandable, eligible, unavailableReason,
                unavailableReasonKey, successRate, estimatedSeconds,
                workRoomDimension, workRoomPosition,
                currentWorkType, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public boolean isWorking() {
        return workBoxCapacity > 0;
    }
}
