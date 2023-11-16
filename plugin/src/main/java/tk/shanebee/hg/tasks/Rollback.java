package tk.shanebee.hg.tasks;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import tk.shanebee.hg.HG;
import tk.shanebee.hg.data.Config;
import tk.shanebee.hg.data.ItemFrameData;
import tk.shanebee.hg.game.Game;
import tk.shanebee.hg.Status;
import tk.shanebee.hg.game.GameBlockData;

/**
 * Rollback task for resetting blocks after a game finishes
 */
public class Rollback implements Runnable {

	private final Iterator<BlockState> session;
	private final Iterator<ItemFrameData> itemFrameDataIterator;
	private final Game game;
	private final GameBlockData gameBlockData;
	private final int blocks_per_second;
	private int timerID;

	public Rollback(Game game) {
		this.game = game;
		this.gameBlockData = game.getGameBlockData();
		this.blocks_per_second = Config.blocks_per_second / 10;
		game.getGameArenaData().setStatus(Status.ROLLBACK);
		this.session = gameBlockData.getBlocks().iterator();
		this.itemFrameDataIterator = gameBlockData.getItemFrameData().iterator();
		timerID = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(HG.getPlugin(), this, 2);
	}

	public void run() {
		int i = 0;
		// Rollback blocks
		while (i < blocks_per_second && session.hasNext()) {
		    BlockState state = session.next();
		    if (state != null) {
                state.update(true);
            }
			i++;
		}
		if (session.hasNext()) {
			timerID = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(HG.getPlugin(), this, 2);
			return;
		}

		// Rollback item frames
		while (itemFrameDataIterator.hasNext()) {
		    ItemFrameData data = itemFrameDataIterator.next();
		    if (data != null) {
		        data.resetItem();
            }
        }

        Bukkit.getServer().getScheduler().cancelTask(timerID);
        gameBlockData.resetBlocks();
        gameBlockData.resetItemFrames();
        game.getGameArenaData().setStatus(Status.READY);
	}

}
