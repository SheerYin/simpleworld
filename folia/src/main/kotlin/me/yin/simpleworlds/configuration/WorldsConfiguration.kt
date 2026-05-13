package me.yin.simpleworlds.configuration

import me.yin.simpleworlds.SimpleWorlds
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

class WorldsConfiguration(val simpleWorlds: SimpleWorlds) {

    val path: Path = simpleWorlds.dataPath.resolve("worlds.yml")
    val loader: YamlConfigurationLoader = YamlConfigurationLoader.builder()
        .path(path)
        .nodeStyle(NodeStyle.BLOCK)
        .indent(2)
        .build()

    fun load(): Worlds {
        val node = loader.load()
        val worlds = node.get(Worlds::class.java) ?: Worlds()
        return worlds
    }

    fun save(worlds: Worlds) {
        val node = loader.createNode()
        node.set(Worlds::class.java, worlds)
        loader.save(node)
    }

    @ConfigSerializable
    class Worlds(
        val worlds: HashMap<String, WorldSection> = hashMapOf()
    ) {
        @ConfigSerializable
        class WorldSection(
            val seed: Long? = null,
            val environment: String = "",
            val type: String? = null,
            val generator: String? = null,
            val difficulty: String = "",
            val gameRule: HashMap<String, String> = hashMapOf()
        )
    }
}
