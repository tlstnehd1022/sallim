package sallim.common.domain

abstract class Identifier<T>(val value: T) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Identifier<*>
        return value == other.value
    }

    override fun hashCode(): Int = value?.hashCode() ?: 0

    override fun toString(): String = "${this::class.simpleName}($value)"
}
