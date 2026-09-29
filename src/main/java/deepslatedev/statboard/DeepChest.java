package deepslatedev.statboard;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BlockActorDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.inventory.fake.FakeInventory;
import org.powernukkitx.inventory.fake.FakeInventoryType;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.utils.RuntimeBlockDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DeepChest extends FakeInventory {
    private static final Map<UUID, DeepChest> PLACED = new ConcurrentHashMap<>();

    private final boolean large;
    private final Map<Player, List<Vector3>> positions = new HashMap<>();
    private Consumer<Player> closeHandler;

    public DeepChest(String title, boolean large) {
        super(large ? FakeInventoryType.DOUBLE_CHEST : FakeInventoryType.CHEST, title);
        this.large = large;
    }

    @Override
    public void setOnCloseHandler(Consumer<Player> handler) {
        this.closeHandler = handler;
    }

    private List<Vector3> placeFor(Player player) {
        Level level = player.getLevel();
        int x = player.getFloorX();
        int z = player.getFloorZ();
        int y = player.getFloorY() - 2;
        if (y < level.getMinHeight()) {
            y = player.getFloorY() + 3;
        }
        List<Vector3> list = new ArrayList<>();
        list.add(new Vector3(x, y, z));
        if (large) {
            list.add(new Vector3((x & 1) == 1 ? x + 1 : x - 1, y, z));
        }
        return list;
    }

    private void sendChest(Player player, Vector3 position, Vector3 pair) {
        Vector3i at = Vector3i.from(position.getFloorX(), position.getFloorY(), position.getFloorZ());
        UpdateBlockPacket block = new UpdateBlockPacket();
        block.setBlockPosition(at);
        block.setDefinition(new RuntimeBlockDefinition(Block.get("minecraft:chest").getRuntimeId()));
        player.sendPacket(block);
        CompoundTag tag = BlockEntity.getDefaultCompound(position, "Chest")
                .putBoolean("isMovable", true)
                .putString("CustomName", getTitle());
        if (pair != null) {
            tag.putInt("pairx", pair.getFloorX()).putInt("pairz", pair.getFloorZ());
        }
        BlockActorDataPacket data = new BlockActorDataPacket();
        data.setBlockPosition(at);
        data.setActorDataTags(tag.toNetwork());
        player.sendPacket(data);
    }

    void restore(Player player) {
        List<Vector3> list = positions.remove(player);
        PLACED.remove(player.getUniqueId(), this);
        if (list == null || list.isEmpty() || !player.isOnline()) {
            return;
        }
        Level level = player.getLevel();
        level.sendBlocks(new Player[]{player}, list.stream().map(level::getBlock).toArray(Block[]::new), Set.of(UpdateBlockPacket.Flag.NETWORK), 0);
    }

    @Override
    public void onOpen(Player player) {
        DeepChest previous = PLACED.get(player.getUniqueId());
        if (previous != null && previous != this) {
            previous.restore(player);
        }
        List<Vector3> list = placeFor(player);
        positions.put(player, list);
        PLACED.put(player.getUniqueId(), this);
        sendChest(player, list.get(0), list.size() > 1 ? list.get(1) : null);
        if (list.size() > 1) {
            sendChest(player, list.get(1), list.get(0));
        }
        player.setFakeInventoryOpen(true);
        player.waitForAck(() -> {
            if (!player.isOnline() || positions.get(player) != list) {
                return;
            }
            Vector3 first = list.get(0);
            ContainerOpenPacket open = new ContainerOpenPacket();
            open.setContainerID((byte) player.getWindowId(this));
            open.setContainerType(getType());
            open.setPosition(Vector3i.from(first.getFloorX(), first.getFloorY(), first.getFloorZ()));
            player.sendPacket(open);
            viewers.add(player);
            sendContents(player);
        });
    }

    @Override
    public void onClose(Player player) {
        ContainerClosePacket close = new ContainerClosePacket();
        close.setContainerID((byte) player.getWindowId(this));
        close.setServerInitiatedClose(player.getClosingWindowId() != close.getContainerID());
        close.setContainerType(getType());
        player.sendPacket(close);
        restore(player);
        viewers.remove(player);
        player.setFakeInventoryOpen(false);
        if (closeHandler != null) {
            closeHandler.accept(player);
        }
    }
}
