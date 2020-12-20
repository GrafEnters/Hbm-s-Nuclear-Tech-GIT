package com.hbm.tileentity.machine;

import com.hbm.forgefluid.FFUtils;
import com.hbm.forgefluid.ModForgeFluids;
import com.hbm.interfaces.IConsumer;
import com.hbm.interfaces.ITankPacketAcceptor;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.packet.AuxElectricityPacket;
import com.hbm.packet.FluidTankPacket;
import com.hbm.packet.PacketDispatcher;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntityMachineCMBFactory extends TileEntity implements ITickable, IConsumer, IFluidHandler, ITankPacketAcceptor {

	public ItemStackHandler inventory;
	
	public long power = 0;
	public int process = 0;
	public int soundCycle = 0;
	public static final long maxPower = 100000000;
	public static final int processSpeed = 200;
	public FluidTank tank;
	public Fluid tankType = ModForgeFluids.watz;
	public boolean needsUpdate = false;

	//private static final int[] slots_top = new int[] {1, 3};
	//private static final int[] slots_bottom = new int[] {0, 2, 4};
	//private static final int[] slots_side = new int[] {0, 2};
	
	private String customName;
	
	public TileEntityMachineCMBFactory() {
		inventory = new ItemStackHandler(6){
			@Override
			protected void onContentsChanged(int slot) {
				markDirty();
				super.onContentsChanged(slot);
			}
			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				if(slot == 1){
					if(stack.getItem() == ModItems.ingot_magnetized_tungsten || stack.getItem() == ModItems.powder_magnetized_tungsten){
						return true;
					} else {
						return false;
					}
				}
				if(slot == 3){
					if(stack.getItem() == ModItems.ingot_advanced_alloy || stack.getItem() == ModItems.powder_advanced_alloy){
						return true;
					} else {
						return false;
					}
				}
				if(slot == 4){
					return false;
				}
				return true;
			}
			@Override
			public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
				if(isItemValid(slot, stack))
					return super.insertItem(slot, stack, simulate);
				else
					return ItemStack.EMPTY;
			}
		};
		tank = new FluidTank(8000);
	}
	
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.machineCMB";
	}

	public boolean hasCustomInventoryName() {
		return this.customName != null && this.customName.length() > 0;
	}
	
	public void setCustomName(String name) {
		this.customName = name;
	}
	
	public boolean isUseableByPlayer(EntityPlayer player) {
		if(world.getTileEntity(pos) != this)
		{
			return false;
		}else{
			return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <=64;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		power = compound.getLong("power");
		tank.readFromNBT(compound);
		process = compound.getShort("process");
		if(compound.hasKey("inventory"))
			inventory.deserializeNBT(compound.getCompoundTag("inventory"));
		super.readFromNBT(compound);
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound.setLong("power", power);
		tank.writeToNBT(compound);
		compound.setShort("process", (short) process);
		compound.setTag("inventory", inventory.serializeNBT());
		return super.writeToNBT(compound);
	}
	
	public long getPowerScaled(long i) {
		return (power * i) / maxPower;
	}
	
	public int getProgressScaled(int i) {
		return (process * i) / processSpeed;
	}
	
	public boolean canProcess() {
		
		boolean b = false;
		
		if(tank.getFluidAmount() >= 1 && power >= 100000 && !inventory.getStackInSlot(1).isEmpty() && !inventory.getStackInSlot(3).isEmpty() && (inventory.getStackInSlot(4).isEmpty() || inventory.getStackInSlot(4).getCount() <= 60))
		{
			boolean flag0 = inventory.getStackInSlot(1).getItem() == ModItems.ingot_magnetized_tungsten || inventory.getStackInSlot(1).getItem() == ModItems.powder_magnetized_tungsten;
			boolean flag1 = inventory.getStackInSlot(3).getItem() == ModItems.ingot_advanced_alloy || inventory.getStackInSlot(3).getItem() == ModItems.powder_advanced_alloy;
			
			b = flag0 && flag1;
		}
		
		return  b;
	}
	
	public boolean isProcessing() {
		return process > 0;
	}
	
	public void process() {
		tank.drain(1, true);
		needsUpdate = true;
		power -= 100000;
		
		process++;
		
		if(process >= processSpeed) {
			
			inventory.getStackInSlot(1).shrink(1);
			if (inventory.getStackInSlot(1).isEmpty()) {
				inventory.setStackInSlot(1, ItemStack.EMPTY);
			}

			inventory.getStackInSlot(3).shrink(1);
			if (inventory.getStackInSlot(3).isEmpty()) {
				inventory.setStackInSlot(3, ItemStack.EMPTY);
			}
			
			if(inventory.getStackInSlot(4).isEmpty())
			{
				inventory.setStackInSlot(4, new ItemStack(ModItems.ingot_combine_steel, 4));
			} else {
				//Possible dupe glitch? Check later.
				inventory.getStackInSlot(4).grow(4);
			}
			
			process = 0;
		}
	}
	
	@Override
	public void update() {
		if (!world.isRemote) {
			long prevPower = power;
			int prevAmount = tank.getFluidAmount();
			if (needsUpdate) {
				needsUpdate = false;
			}
			power = Library.chargeTEFromItems(inventory, 0, power, maxPower);
			
			if(this.inputValidForTank(-1, 2))
				if(FFUtils.fillFromFluidContainer(inventory, tank, 2, 5))
					needsUpdate = true;

			if (canProcess()) {
				process();
				if(soundCycle == 0)
			        this.world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.BLOCKS, 1.0F, 1.5F);
				soundCycle++;
					
				if(soundCycle >= 25)
					soundCycle = 0;
			} else {
				process = 0;
			}

			PacketDispatcher.wrapper.sendToAllAround(new AuxElectricityPacket(pos, power), new TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 10));
			PacketDispatcher.wrapper.sendToAllAround(new FluidTankPacket(pos, new FluidTank[] {tank}), new TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 10));
			if(prevPower != power || prevAmount != tank.getFluidAmount()){
				markDirty();
			}
		}
	}
	
	protected boolean inputValidForTank(int tank, int slot){
		if(!inventory.getStackInSlot(slot).isEmpty()){
			if(isValidFluid(FluidUtil.getFluidContained(inventory.getStackInSlot(slot)))){
				return true;	
			}
		}
		return false;
	}
	
	private boolean isValidFluid(FluidStack stack) {
		if(stack == null)
			return false;
		return stack.getFluid() == ModForgeFluids.watz;
	}
	
	@Override
	public void setPower(long i) {
		power = i;
		
	}

	@Override
	public long getPower() {
		return power;
		
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	@Override
	public IFluidTankProperties[] getTankProperties() {
		return new IFluidTankProperties[]{tank.getTankProperties()[0]};
	}

	@Override
	public int fill(FluidStack resource, boolean doFill) {
		if (isValidFluid(resource)) {
			if(tank.fill(resource, false) > 0)
				needsUpdate = true;
			return tank.fill(resource, doFill);
		}
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, boolean doDrain) {
		return null;
	}

	@Override
	public FluidStack drain(int maxDrain, boolean doDrain) {
		return null;
	}

	@Override
	public void recievePacket(NBTTagCompound[] tags) {
		if(tags.length != 1) {
			return;
		} else {
			tank.readFromNBT(tags[0]);
		}
	}
	
	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if(capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY){
			return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
		} else if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
			return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this);
		} else {
			return super.getCapability(capability, facing);
		}
	}
	
	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if(capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY){
			return true;
		} else if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
			return true;
		} else {
			return super.hasCapability(capability, facing);
		}
	}

}