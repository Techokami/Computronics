package pl.asie.computronics;

import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import li.cil.oc.api.Driver;
import openperipheral.api.OpenPeripheralAPI;
import pl.asie.computronics.audio.DFPWMPlaybackManager;
import pl.asie.computronics.block.BlockCamera;
import pl.asie.computronics.block.BlockChatBox;
import pl.asie.computronics.block.BlockCipher;
import pl.asie.computronics.block.BlockIronNote;
import pl.asie.computronics.block.BlockSorter;
import pl.asie.computronics.block.BlockTapeReader;
import pl.asie.computronics.gui.GuiOneSlot;
import pl.asie.computronics.item.ItemOpenComputers;
import pl.asie.computronics.item.ItemTape;
import pl.asie.computronics.storage.StorageManager;
import pl.asie.computronics.tile.ContainerTapeReader;
import pl.asie.computronics.tile.TileCamera;
import pl.asie.computronics.tile.TileChatBox;
import pl.asie.computronics.tile.TileCipherBlock;
import pl.asie.computronics.tile.TileIronNote;
import pl.asie.computronics.tile.TileTapeDrive;
import pl.asie.computronics.tile.sorter.TileSorter;
import pl.asie.lib.gui.GuiHandler;
import pl.asie.lib.item.ItemMultiple;
import pl.asie.lib.network.PacketHandler;
import pl.asie.lib.util.ModIntegrationHandler;
import pl.asie.lib.util.ModIntegrationHandler.Stage;
import pl.asie.lib.util.color.RecipeColorizer;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.ShapedOreRecipe;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

@Mod(modid="computronics", name="Computronics", version="0.4.1", dependencies="required-after:asielib;after:OpenPeripheralCore;after:ComputerCraft;after:OpenComputers;after:OpenComputers|Core;after:BuildCraft|Core")
public class Computronics {
	public Configuration config;
	public static Random rand = new Random();
	public static Logger log;
	
	@Instance(value="computronics")
	public static Computronics instance;
	public static StorageManager storage;
	public static GuiHandler gui;
	public static PacketHandler packet;
	public DFPWMPlaybackManager audio;
	
	public static int CHATBOX_DISTANCE = 40;
	public static int CAMERA_DISTANCE = 32;
	public static int TAPEDRIVE_DISTANCE = 24;
	public static int BUFFER_MS = 750;
	public static String CHATBOX_PREFIX = "[ChatBox]";
	public static boolean CAMERA_REDSTONE_REFRESH, CHATBOX_ME_DETECT, CHATBOX_CREATIVE;
	
	@SidedProxy(clientSide="pl.asie.computronics.ClientProxy", serverSide="pl.asie.computronics.CommonProxy")	
	public static CommonProxy proxy;
	
	public static BlockIronNote ironNote;
	public static BlockTapeReader tapeReader;
	public static BlockCamera camera;
	public static BlockChatBox chatBox;
	public static BlockSorter sorter;
	public static BlockCipher cipher;
	
	public static ItemTape itemTape;
	public static ItemMultiple itemParts;
	public static ItemOpenComputers itemRobotUpgrade;
	
	//public static Class<? extends TileEntity> CHAT_BOX_CLASS;
	
	public static CreativeTabs tab = new CreativeTabs("tabComputronics") {
        public Item getTabIconItem() {
                return itemTape;
        }
	};

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		log = LogManager.getLogger("computronics");

		config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		
		audio = new DFPWMPlaybackManager(proxy.isClient());
		packet = new PacketHandler("computronics", new NetworkHandlerClient(), new NetworkHandlerServer());
		
		// Configs
		CHATBOX_DISTANCE = config.get("chatbox", "maxDistance", 40).getInt();
		CAMERA_DISTANCE = config.get("camera", "maxDistance", 32).getInt();
		CAMERA_REDSTONE_REFRESH = config.get("camera", "sendRedstoneSignal", true).getBoolean(true);
		BUFFER_MS = config.get("tapedrive", "audioPreloadMs", 750).getInt();
		CHATBOX_PREFIX = config.get("chatbox", "prefix", "[ChatBox]").getString();
		CHATBOX_ME_DETECT = config.get("chatbox", "readCommandMe", false).getBoolean(false);
		CHATBOX_CREATIVE = config.get("chatbox", "enableCreative", true).getBoolean(true);
		TAPEDRIVE_DISTANCE = config.get("tapedrive", "hearingDistance", 24).getInt();
		
		config.get("camera", "sendRedstoneSignal", true).comment = "Setting this to false might help Camera tick lag issues, at the cost of making them useless with redstone circuitry.";
		
		ironNote = new BlockIronNote();
		GameRegistry.registerBlock(ironNote, "computronics.ironNoteBlock");
		GameRegistry.registerTileEntity(TileIronNote.class, "computronics.ironNoteBlock");

		tapeReader = new BlockTapeReader();
		GameRegistry.registerBlock(tapeReader, "computronics.tapeReader");
		GameRegistry.registerTileEntity(TileTapeDrive.class, "computronics.tapeReader");

		camera = new BlockCamera();
		GameRegistry.registerBlock(camera, "computronics.camera");
		GameRegistry.registerTileEntity(TileCamera.class, "computronics.camera");

		chatBox = new BlockChatBox();
		GameRegistry.registerBlock(chatBox, "computronics.chatBox");
		GameRegistry.registerTileEntity(TileChatBox.class, "computronics.chatBox");

		//sorter = new BlockSorter();
		//GameRegistry.registerBlock(sorter, "computronics.sorter");
		//GameRegistry.registerTileEntity(TileSorter.class, "computronics.sorter");

		cipher = new BlockCipher();
		GameRegistry.registerBlock(cipher, "computronics.cipher");
		GameRegistry.registerTileEntity(TileCipherBlock.class, "computronics.cipher");

		if(Loader.isModLoaded("OpenPeripheralCore")) {
			OpenPeripheralAPI.createAdapter(TileTapeDrive.class);
			OpenPeripheralAPI.createAdapter(TileIronNote.class);
			OpenPeripheralAPI.createAdapter(TileCamera.class);
			//OpenPeripheralAPI.createAdapter(TileSorter.class);
			OpenPeripheralAPI.createAdapter(TileCipherBlock.class);
		}			
		
		itemTape = new ItemTape();
		GameRegistry.registerItem(itemTape, "computronics.tape");
		
		itemParts = new ItemMultiple("computronics", new String[]{"part_tape_track"});
		itemParts.setCreativeTab(tab);
		GameRegistry.registerItem(itemParts, "computronics.parts");
		
		if(Loader.isModLoaded("OpenComputers")) {
			itemRobotUpgrade = new ItemOpenComputers();
			GameRegistry.registerItem(itemRobotUpgrade, "computronics.robotUpgrade");
			Driver.add(itemRobotUpgrade);
		}
	}
	
	@EventHandler
	public void init(FMLInitializationEvent event) {
		gui = new GuiHandler();
		NetworkRegistry.INSTANCE.registerGuiHandler(Computronics.instance, gui);
		
		MinecraftForge.EVENT_BUS.register(new ComputronicsEventHandler());
		
		proxy.registerGuis(gui);
		
		FMLInterModComms.sendMessage("Waila", "register", "pl.asie.computronics.integration.waila.IntegrationWaila.register");
		
		GameRegistry.addShapedRecipe(new ItemStack(camera, 1, 0), "sss", "geg", "iii", 's', Blocks.stonebrick, 'i', Items.iron_ingot, 'e', Items.ender_pearl, 'g', Blocks.glass);
		GameRegistry.addShapedRecipe(new ItemStack(chatBox, 1, 0), "sss", "ses", "iri", 's', Blocks.stonebrick, 'i', Items.iron_ingot, 'e', Items.ender_pearl, 'r', Items.redstone);
		GameRegistry.addShapedRecipe(new ItemStack(ironNote, 1, 0), "iii", "ini", "iii", 'i', Items.iron_ingot, 'n', Blocks.noteblock);
		GameRegistry.addShapedRecipe(new ItemStack(tapeReader, 1, 0), "iii", "iri", "iai", 'i', Items.iron_ingot, 'r', Items.redstone, 'a', ironNote);
		GameRegistry.addShapedRecipe(new ItemStack(cipher, 1, 0), "sss", "srs", "eie", 'i', Items.iron_ingot, 'r', Items.redstone, 'e', Items.ender_pearl, 's', Blocks.stonebrick);
		// Tape recipes
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 0),
				" i ", "iii", " T ", 'T', new ItemStack(itemParts, 1, 0), 'i', Items.iron_ingot));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 1),
				" i ", "ngn", " T ", 'T', new ItemStack(itemParts, 1, 0), 'i', Items.iron_ingot, 'n', Items.gold_nugget, 'g', Items.gold_ingot));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 2),
				" i ", "ggg", "nTn", 'T', new ItemStack(itemParts, 1, 0), 'i', Items.iron_ingot, 'n', Items.gold_nugget, 'g', Items.gold_ingot));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 3),
				" i ", "ddd", " T ", 'T', new ItemStack(itemParts, 1, 0), 'i', Items.iron_ingot, 'd', Items.diamond));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 4),
				" d ", "dnd", " T ", 'T', new ItemStack(itemParts, 1, 0), 'n', Items.nether_star, 'd', Items.diamond));
		
		// Mod compat - copper/steel
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 5),
				" i ", " c ", " T ", 'T', new ItemStack(itemParts, 1, 0), 'i', Items.iron_ingot, 'c', "ingotCopper"));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 6),
				" i ", "isi", " T ", 'T', new ItemStack(itemParts, 1, 0), 'i', Items.iron_ingot, 's', "ingotSteel"));
		
		// Mod compat - GregTech
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemTape, 1, 7),
				" i ", "isi", " T ", 'T', new ItemStack(itemParts, 1, 0), 'i', "plateIridium", 's', "plateTungstenSteel"));
				
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(itemParts, 1, 0),
				" i ", "rrr", "iii", 'r', Items.redstone, 'i', Items.iron_ingot));
		GameRegistry.addRecipe(new RecipeColorizer(itemTape));
		
		if(Loader.isModLoaded("OpenComputers")) {
			GameRegistry.addShapedRecipe(new ItemStack(itemRobotUpgrade, 1, 0), "mcm", 'c', new ItemStack(camera, 1, 0), 'm', li.cil.oc.api.Items.MicroChipTier2);
			GameRegistry.addShapedRecipe(new ItemStack(itemRobotUpgrade, 1, 0), "m", "c", "m", 'c', new ItemStack(camera, 1, 0), 'm', li.cil.oc.api.Items.MicroChipTier2);
		}
		config.save();
	}
	
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
	}
	
	@EventHandler
	public void serverStart(FMLServerAboutToStartEvent event) {
		Computronics.storage = new StorageManager();
	}
}
