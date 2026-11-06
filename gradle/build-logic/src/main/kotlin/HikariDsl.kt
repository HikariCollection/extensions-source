import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

enum class ContentWarning {
    SAFE,
    MIXED,
    NSFW,
}

abstract class HikariExtension @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val theme: Property<String>
    abstract val extensionClass: Property<String>
    abstract val versionCode: Property<Int>
    abstract val libVersion: Property<String>
    abstract val contentWarning: Property<ContentWarning>
    abstract val pkgName: Property<String>
    abstract val sources: ListProperty<ExtensionSource>

    fun source(block: ExtensionSource.() -> Unit) {
        sources.add(objects.newInstance<ExtensionSource>().apply(block))
    }
}

abstract class HikariThemeExtension {
    abstract val baseVersionCode: Property<Int>
    abstract val libVersion: Property<String>
}

abstract class ExtensionSource {
    abstract val name: Property<String>
    abstract val lang: Property<String>
    abstract val baseUrlProperty: Property<String>
    abstract val versionId: Property<Int>
    abstract val id: Property<Long>

    var baseUrl: String
        get() = baseUrlProperty.getOrElse("")
        set(value) {
            baseUrlProperty.set(value)
        }
}
