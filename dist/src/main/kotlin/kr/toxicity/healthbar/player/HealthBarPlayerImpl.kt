package kr.toxicity.healthbar.player

import kr.toxicity.healthbar.api.entity.HealthBarEntity
import kr.toxicity.healthbar.api.healthbar.HealthBar
import kr.toxicity.healthbar.api.healthbar.HealthBarUpdaterGroup
import kr.toxicity.healthbar.api.player.HealthBarPlayer
import kr.toxicity.healthbar.api.trigger.HealthBarTrigger
import kr.toxicity.healthbar.healthbar.HealthBarUpdaterGroupImpl
import kr.toxicity.healthbar.manager.ConfigManagerImpl
import kr.toxicity.healthbar.util.PLUGIN
import kr.toxicity.healthbar.util.asyncTaskTimer
import org.bukkit.entity.Player
import java.util.*

class HealthBarPlayerImpl(
    private val player: Player
): HealthBarPlayer {
    override fun player(): Player = player
    override fun compareTo(other: HealthBarPlayer): Int = player.uniqueId.compareTo(other.player().uniqueId)

    private val updaterMap = HashMap<UUID, HealthBarUpdaterGroup>()
    private val task = asyncTaskTimer(1, 1) {
        synchronized(updaterMap) {
            val iterator = updaterMap.values.iterator()
            synchronized(iterator) {
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if (!next.update()) iterator.remove()
                }
            }
        }
    }

    init {
        PLUGIN.nms().inject(this)
    }

    override fun uninject() {
        task.cancel()
        PLUGIN.nms().uninject(this)
    }

    override fun clear() {
        synchronized(updaterMap) {
            val iterator = updaterMap.values.iterator()
            synchronized(iterator) {
                while (iterator.hasNext()) {
                    iterator.next().remove()
                }
            }
            updaterMap.clear()
        }
    }

    override fun updaterMap(): MutableMap<UUID, HealthBarUpdaterGroup> = updaterMap

    override fun showHealthBar(healthBar: HealthBar, trigger: HealthBarTrigger, entity: HealthBarEntity) {
        if (ConfigManagerImpl.blacklistEntityType().contains(entity.entity().type)) return
        if (ConfigManagerImpl.disableToInvulnerableMob() && entity.entity().isInvulnerable) return
        if (!ConfigManagerImpl.showMeHealthBar() && player.uniqueId == entity.entity().uniqueId) return
        entity.mob()?.let {
            if (it.configuration().blacklist()) return
        }
        synchronized(updaterMap) {
            updaterMap.computeIfAbsent(entity.entity().uniqueId) {
                HealthBarUpdaterGroupImpl(this, entity)
            }.addHealthBar(healthBar, trigger)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HealthBarPlayerImpl

        return player.uniqueId == other.player.uniqueId
    }

    override fun hashCode(): Int {
        return player.uniqueId.hashCode()
    }
}