package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.VillagerHouse;
import com.solegendary.reignofnether.building.buildings.VillagerTower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

public abstract class Building {

    public String name;
    // building collapses at a certain % blocks remaining so players don't have to destroy every single block
    public final float minBlocksPercent = 0.2f;
    public boolean isBuilt; // set true when blocksPercent reaches 100% the first time
    public boolean isBuilding = true; // TODO: only true if // a builder is assigned and actively building or repairing
    public int ticksPerBuild = 6; // ticks taken to place a single block while isBuilding
    public int ticksToNextBuild = ticksPerBuild;
    // chance for a mini explosion to destroy extra blocks if a player is breaking it
    // should be higher for large fragile buildings so players don't take ages to destroy it
    public float explodeChance;
    protected ArrayList<BuildingBlock> blocks = new ArrayList<>();
    public String ownerName;
    public Block portraitBlock; // block rendered in the portrait GUI to represent this building
    public int tickAge = 0; // how many ticks ago this building was placed

    public Building() {
    }

    // given a string name return a new instance of that building
    public static Building getNewBuilding(String buildingName, LevelAccessor level, BlockPos pos, Rotation rotation, String ownerName) {
        Building building = null;
        switch(buildingName) {
            case VillagerHouse.buildingName -> building = new VillagerHouse(level, pos, rotation, ownerName);
            case VillagerTower.buildingName -> building = new VillagerTower(level, pos, rotation, ownerName);
        }
        return building;
    }

    public static Vec3i getMinCorner(ArrayList<BuildingBlock> blocks) {
        return new Vec3i(
            blocks.stream().min(Comparator.comparing(block -> block.getBlockPos().getX())).get().getBlockPos().getX(),
            blocks.stream().min(Comparator.comparing(block -> block.getBlockPos().getY())).get().getBlockPos().getY(),
            blocks.stream().min(Comparator.comparing(block -> block.getBlockPos().getZ())).get().getBlockPos().getZ()
        );
    }
    public static Vec3i getMaxCorner(ArrayList<BuildingBlock> blocks) {
        return new Vec3i(
            blocks.stream().max(Comparator.comparing(block -> block.getBlockPos().getX())).get().getBlockPos().getX(),
            blocks.stream().max(Comparator.comparing(block -> block.getBlockPos().getY())).get().getBlockPos().getY(),
            blocks.stream().max(Comparator.comparing(block -> block.getBlockPos().getZ())).get().getBlockPos().getZ()
        );
    }

    public boolean isPosInsideBuilding(BlockPos bp) {
        Vec3i min = getMinCorner(this.blocks);
        Vec3i max = getMaxCorner(this.blocks);

        return bp.getX() <= max.getX() && bp.getX() >= min.getX() &&
               bp.getY() <= max.getY() && bp.getY() >= min.getY() &&
               bp.getZ() <= max.getZ() && bp.getZ() >= min.getZ();
    }

    public static Vec3i getBuildingSize(ArrayList<BuildingBlock> blocks) {
        Vec3i min = getMinCorner(blocks);
        Vec3i max = getMaxCorner(blocks);
        return new Vec3i(
                max.getX() - min.getX(),
                max.getY() - min.getY(),
                max.getZ() - min.getZ()
        );
    }

    // get BlockPos values with absolute world positions
    public static ArrayList<BuildingBlock> getAbsoluteBlockData(ArrayList<BuildingBlock> staticBlocks, LevelAccessor level, BlockPos originPos, Rotation rotation) {
        ArrayList<BuildingBlock> blocks = new ArrayList<>();

        for (BuildingBlock block : staticBlocks) {
            block = block.rotate(level, rotation);
            BlockPos bp = block.getBlockPos();

            block.setBlockPos(new BlockPos(
                bp.getX() + originPos.getX(),
                bp.getY() + originPos.getY() + 1,
                bp.getZ() + originPos.getZ()
            ));
            blocks.add(block);
        }
        return blocks;
    }

    // get BlockPos values with relative positions
    public static ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) { return new ArrayList<>(); }

    public int getBlocksTotal() {
        return blocks.stream().filter(b -> !b.getBlockState().isAir()).toList().size();
    }
    public int getBlocksPlaced() {
        return blocks.stream().filter(b -> b.isPlaced && !b.getBlockState().isAir()).toList().size();
    }
    public float getBlocksPercent() {
        return (float) getBlocksPlaced() / (float) getBlocksTotal();
    }
    public boolean isFunctional() {
        return this.isBuilt && this.getBlocksPercent() >= 0.5f;
    }

    // place blocks according to the following rules:
    // - block must be connected to something else (not air)
    // - block must be the lowest Y value possible
    private void buildNextBlock(Level level) {
        ArrayList<BuildingBlock> unplacedBlocks = new ArrayList<>(blocks.stream().filter(b -> !b.isPlaced).toList());
        int minY = getMinCorner(unplacedBlocks).getY();
        ArrayList<BuildingBlock> validBlocks = new ArrayList<>();

        // iterate through unplaced blocks and start at the bottom Y values
        for (BuildingBlock block : unplacedBlocks) {
            BlockPos bp = block.getBlockPos();
            if ((bp.getY() <= minY) &&
                (!level.getBlockState(bp.below()).isAir() ||
                 !level.getBlockState(bp.east()).isAir() ||
                 !level.getBlockState(bp.west()).isAir() ||
                 !level.getBlockState(bp.south()).isAir() ||
                 !level.getBlockState(bp.north()).isAir() ||
                 !level.getBlockState(bp.above()).isAir()))
                validBlocks.add(block);
        }
        if (validBlocks.size() > 0) {
            validBlocks.get(0).place();
            /*
            Random rand = new Random();
            validBlocks.get(rand.nextInt(validBlocks.size())).place();
            */
        }
    }

    // destroy all remaining blocks in a final big explosion
    private void destroy() {

    }

    // should only be run serverside
    public void onBlockBreak(BlockEvent.BreakEvent evt) {
        // when a player breaks a block that's part of the building:
        // - roll explodeChance to cause explosion effects and destroy more blocks
        // - cause fire if < 50% blocksPercent
    }

    public void onWorldTick(Level level) {
        this.tickAge += 1;

        boolean isClientSide = level.isClientSide();

        // update all the BuildingBlock.isPlaced booleans to match what the world actually has
        for (BuildingBlock block : blocks) {
            BlockPos bp = block.getBlockPos();
            BlockState bs = block.getBlockState();
            BlockState bsWorld = level.getBlockState(bp);
            block.isPlaced = bsWorld.equals(bs);
        }

        if (!isClientSide) {
            float blocksPercent = getBlocksPercent();
            float blocksPlaced = getBlocksPlaced();
            float blocksTotal = getBlocksTotal();

            if (blocksPlaced <= 0)
                destroy();

            // TODO: if builder is assigned, set isBuilding true

            // place a block if the tick has run down
            if (isBuilding && blocksPlaced < blocksTotal) {
                ticksToNextBuild -= 1;
                if (ticksToNextBuild <= 0) {
                    ticksToNextBuild = ticksPerBuild;
                    buildNextBlock(level);
                }
            }
            else { // start destroying random blocks once built (for testing purposes)
                ticksToNextBuild -= 1;
                if (ticksToNextBuild <= 0) {
                    ticksToNextBuild = ticksPerBuild;
                    Random random = new Random();
                    int randint = random.nextInt(blocks.size());
                    blocks.get(randint).destroy();
                }
            }

            if (blocksPlaced >= blocksTotal)
                isBuilding = false;

            // TODO: if fires exist, put them out one by one (or gradually remove them if blocksPercent > 50%)
        }
    }
}