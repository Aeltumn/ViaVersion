package us.myles.ViaVersion.protocols.protocol1_9to1_8;

import com.google.gson.JsonObject;
import us.myles.ViaVersion.api.PacketWrapper;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.data.UserConnection;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.minecraft.metadata.Metadata;
import us.myles.ViaVersion.api.platform.providers.ViaProviders;
import us.myles.ViaVersion.api.protocol.Protocol;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.remapper.ValueTransformer;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.api.type.types.version.Metadata1_8Type;
import us.myles.ViaVersion.api.type.types.version.MetadataList1_8Type;
import us.myles.ViaVersion.packets.State;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.packets.*;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.BulkChunkTranslatorProvider;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.HandItemProvider;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.storage.*;
import us.myles.ViaVersion.util.GsonUtil;

import java.util.List;

public class Protocol1_9TO1_8 extends Protocol {
    public static final ValueTransformer<String, String> FIX_JSON = new ValueTransformer<String, String>(Type.STRING) {
        @Override
        public String transform(PacketWrapper wrapper, String line) {
            return fixJson(line);
        }
    };
    @Deprecated
    public static Type<List<Metadata>> METADATA_LIST = new MetadataList1_8Type();
    @Deprecated
    public static Type<Metadata> METADATA = new Metadata1_8Type();

    public static String fixJson(String line) {
        if (line == null || line.equalsIgnoreCase("null")) {
            line = "{\"text\":\"\"}";
        } else {
            if ((!line.startsWith("\"") || !line.endsWith("\"")) && (!line.startsWith("{") || !line.endsWith("}"))) {
                return constructJson(line);
            }
            if (line.startsWith("\"") && line.endsWith("\"")) {
                line = "{\"text\":" + line + "}";
            }
        }
        try {
            GsonUtil.getGson().fromJson(line, JsonObject.class);
        } catch (Exception e) {
            if (Via.getConfig().isForceJsonTransform()) {
                return constructJson(line);
            } else {
                System.out.println("Invalid JSON String: \"" + line + "\" Please report this issue to the ViaVersion Github: " + e.getMessage());
                return "{\"text\":\"\"}";
            }
        }
        return line;
    }

    private static String constructJson(String text) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", text);
        return GsonUtil.getGson().toJson(jsonObject);
    }

    public static Item getHandItem(final UserConnection info) {
        return Via.getManager().getProviders().get(HandItemProvider.class).getHandItem(info);
    }

    public static boolean isSword(int id) {
        if (id == 267) return true; // Iron
        if (id == 268) return true; // Wood
        if (id == 272) return true; // Stone
        if (id == 276) return true; // Diamond
        if (id == 283) return true; // Gold

        return false;
    }

    @Override
    protected void registerPackets() {
        // Disconnect workaround (JSON!)
        registerOutgoing(State.LOGIN, 0x00, 0x00, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.STRING, Protocol1_9TO1_8.FIX_JSON); // 0 - Reason
            }
        });

        // Other Handlers
        SpawnPackets.register(this);
        InventoryPackets.register(this);
        EntityPackets.register(this);
        PlayerPackets.register(this);
        WorldPackets.register(this);
    }

    @Override
    protected void register(ViaProviders providers) {
        providers.register(HandItemProvider.class, new HandItemProvider());
        providers.register(BulkChunkTranslatorProvider.class, new BulkChunkTranslatorProvider());
        providers.require(MovementTransmitterProvider.class);
        if (Via.getConfig().isStimulatePlayerTick()) {
            Via.getPlatform().runRepeatingSync(new ViaIdleThread(), 1L);
        }
    }

    @Override
    public boolean isFiltered(Class packetClass) {
        return Via.getManager().getProviders().get(BulkChunkTranslatorProvider.class).isFiltered(packetClass);

    }

    @Override
    protected void filterPacket(UserConnection info, Object packet, List output) throws Exception {
        output.addAll(info.get(ClientChunks.class).transformMapChunkBulk(packet));
    }

    @Override
    public void init(UserConnection userConnection) {
        // Entity tracker
        userConnection.put(new EntityTracker(userConnection));
        // Chunk tracker
        userConnection.put(new ClientChunks(userConnection));
        // Movement tracker
        userConnection.put(new MovementTracker(userConnection));
        // Inventory tracker
        userConnection.put(new InventoryTracker(userConnection));
        // Place block tracker
        userConnection.put(new PlaceBlockTracker(userConnection));
    }
}