package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.capability.EmployeeStats;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class EmployeeRecord {
    private final UUID id;
    private String name;
    private EmployeeDepartment department;
    private int centralSection;
    private int fortitude;
    private int prudence;
    private int temperance;
    private int justice;
    private String persona = "";
    private EmployeeTaskType taskType = EmployeeTaskType.IDLE;
    private String taskSummary = "";
    private String dimension = "lobotocraft:loboto";
    private double x;
    private double y;
    private double z;
    private boolean deployed;
    private EmployeeAppearance appearance = EmployeeAppearance.DEFAULT;
    // 显式的、不可逆的死亡/退休墓碑标志。死亡是不可逆事件，必须立即显式持久化，
    // 不能依赖延迟落盘或从 Health 推断（否则崩溃/强杀后会复活已死员工）。
    private boolean retired;
    private CompoundTag playerData = new CompoundTag();
    private String manualStationDimension = "";
    private BlockPos manualStationPos;

    public EmployeeRecord(UUID id, String name, EmployeeDepartment department) {
        this.id = id;
        this.name = name;
        this.department = department;
        fortitude = prudence = temperance = justice = EmployeeStats.MIN_STAT;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public EmployeeDepartment department() { return department; }
    public int centralSection() { return centralSection; }
    public int fortitude() { return fortitude; }
    public int prudence() { return prudence; }
    public int temperance() { return temperance; }
    public int justice() { return justice; }
    public String persona() { return persona; }
    public EmployeeTaskType taskType() { return taskType; }
    public String taskSummary() { return taskSummary; }
    public String dimension() { return dimension; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public boolean deployed() { return deployed; }
    public EmployeeAppearance appearance() { return appearance; }
    public boolean retired() { return retired; }
    public CompoundTag playerData() { return playerData.copy(); }
    public ResourceLocation manualStationDimension() {
        return manualStationDimension.isBlank() ? null : ResourceLocation.tryParse(manualStationDimension);
    }
    public BlockPos manualStationPos() { return manualStationPos; }

    public void setTask(EmployeeTaskType taskType, String taskSummary) {
        this.taskType = taskType == null ? EmployeeTaskType.IDLE : taskType;
        this.taskSummary = taskSummary == null ? "" : taskSummary;
    }

    public void setPosition(ResourceLocation dimension, double x, double y, double z) {
        this.dimension = dimension.toString();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setDeployed(boolean deployed) {
        this.deployed = deployed;
    }

    public void setAppearance(EmployeeAppearance appearance) {
        this.appearance = appearance == null ? EmployeeAppearance.DEFAULT : appearance.sanitized();
    }

    public void setManualStation(ResourceLocation dimension, BlockPos position) {
        manualStationDimension = dimension == null ? "" : dimension.toString();
        manualStationPos = position == null ? null : position.immutable();
    }

    public void setRetired(boolean retired) {
        this.retired = retired;
    }

    public void setDepartment(EmployeeDepartment department) {
        this.department = department == null ? EmployeeDepartment.UNASSIGNED : department;
        if (this.department != EmployeeDepartment.CENTRAL) centralSection = 0;
    }

    public void setCentralSection(int centralSection) {
        this.centralSection = department == EmployeeDepartment.CENTRAL
                ? Math.max(0, Math.min(1, centralSection)) : 0;
    }

    public void setStats(int fortitude, int prudence, int temperance, int justice) {
        this.fortitude = Math.max(EmployeeStats.MIN_STAT,
                Math.min(EmployeeStats.MAX_STAT, fortitude));
        this.prudence = Math.max(EmployeeStats.MIN_STAT,
                Math.min(EmployeeStats.MAX_STAT, prudence));
        this.temperance = Math.max(EmployeeStats.MIN_STAT,
                Math.min(EmployeeStats.MAX_STAT, temperance));
        this.justice = Math.max(EmployeeStats.MIN_STAT,
                Math.min(EmployeeStats.MAX_STAT, justice));
    }

    public void setPersona(String persona) {
        this.persona = persona == null ? "" : persona;
    }

    public void updateRuntimeData(CompoundTag playerData, int fortitude, int prudence,
                                  int temperance, int justice) {
        this.playerData = playerData.copy();
        setStats(fortitude, prudence, temperance, justice);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Department", department.id());
        tag.putInt("CentralSection", centralSection);
        tag.putInt("Fortitude", fortitude);
        tag.putInt("Prudence", prudence);
        tag.putInt("Temperance", temperance);
        tag.putInt("Justice", justice);
        tag.putString("Persona", persona);
        tag.putString("TaskType", taskType.name());
        tag.putString("TaskSummary", taskSummary);
        tag.putString("Dimension", dimension);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putBoolean("Deployed", deployed);
        if (!appearance.isDefault()) tag.put("Appearance", appearance.save());
        tag.putBoolean("Retired", retired);
        tag.put("PlayerData", playerData.copy());
        if (manualStationPos != null && !manualStationDimension.isBlank()) {
            tag.putString("ManualStationDimension", manualStationDimension);
            tag.putLong("ManualStationPos", manualStationPos.asLong());
        }
        return tag;
    }

    public static EmployeeRecord load(CompoundTag tag) {
        EmployeeRecord record = new EmployeeRecord(tag.getUUID("Id"), tag.getString("Name"),
                EmployeeDepartment.byId(tag.getString("Department")));
        record.centralSection = record.department == EmployeeDepartment.CENTRAL
                ? Math.max(0, Math.min(1, tag.getInt("CentralSection"))) : 0;
        record.setStats(tag.getInt("Fortitude"), tag.getInt("Prudence"),
                tag.getInt("Temperance"), tag.getInt("Justice"));
        // Missing string tags read as an empty string, keeping old saves compatible.
        record.persona = tag.getString("Persona");
        try {
            record.taskType = EmployeeTaskType.valueOf(tag.getString("TaskType"));
        } catch (IllegalArgumentException ignored) {
            record.taskType = EmployeeTaskType.IDLE;
        }
        record.taskSummary = tag.getString("TaskSummary");
        if (!tag.getString("Dimension").isEmpty()) record.dimension = tag.getString("Dimension");
        record.x = tag.getDouble("X");
        record.y = tag.getDouble("Y");
        record.z = tag.getDouble("Z");
        record.deployed = tag.getBoolean("Deployed");
        record.appearance = EmployeeAppearance.load(tag.getCompound("Appearance"));
        // 老存档没有该字段时 getBoolean 默认返回 false，向后兼容。
        record.retired = tag.getBoolean("Retired");
        record.playerData = tag.getCompound("PlayerData").copy();
        if (tag.contains("ManualStationPos")
                && !tag.getString("ManualStationDimension").isBlank()) {
            record.manualStationDimension = tag.getString("ManualStationDimension");
            record.manualStationPos = BlockPos.of(tag.getLong("ManualStationPos"));
        }
        return record;
    }
}
