package com.skyy.coins.task;

import com.skyy.coins.storage.FileStorage;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * AutoSaveTask — persiste dados em background a cada N minutos.
 *
 * YAML  → flushDirty() (grava apenas perfis alterados desde o último save)
 * MySQL → saveAll()    (batch update de todos os perfis em memória)
 *
 * Também invalida e reconstrói o cache do top players de forma segura
 * nesta thread assíncrona, garantindo que a main thread nunca precise
 * disparar queries para recarregar o cache.
 */
public class AutoSaveTask extends BukkitRunnable {

    private final FileStorage fileStorage;

    public AutoSaveTask(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();

        // Expira o timestamp dos caches (sem anulá-los) para que sejam reconstruídos
        // na próxima chamada a getTopPlayers()/getMagnata() nesta thread async.
        // NÃO usamos invalidate() pois isso cria uma janela onde o cache é null:
        // se uma mudança de coins coincidir com esse instante, onRankChange() recebe
        // lista vazia e os NPCs mostram "Aguardando..." indevidamente.
        fileStorage.expireTopCache();
        fileStorage.expireMagnataCache();

        if (fileStorage.isUsingDatabase()) {
            fileStorage.saveAll();
        } else {
            fileStorage.flushDirty();
        }

        // Reconstrói os caches na thread async (nunca bloqueia a main thread)
        fileStorage.getTopPlayers(10);    // reconstrói top cache
        fileStorage.getMagnata();         // reconstrói magnata cache

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 200) {
            fileStorage.getPlugin().getLogger()
                .warning("[sCoins] Auto-save demorou " + elapsed + "ms. Verifique a conexão com o banco.");
        }
    }
}
