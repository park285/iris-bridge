package party.qwer.iris.imagebridge.runtime.send

import java.util.ArrayDeque

internal fun typeDistance(
    actualType: Class<*>,
    candidateType: Class<*>,
): Int {
    if (actualType == candidateType) return 0
    if (!candidateType.isAssignableFrom(actualType)) return Int.MAX_VALUE
    val visited = linkedSetOf<Class<*>>(actualType)
    val queue = ArrayDeque<Pair<Class<*>, Int>>()
    queue += actualType to 0
    while (queue.isNotEmpty()) {
        val (current, distance) = queue.removeFirst()
        if (current == candidateType) return distance
        current.superclass?.let { superclass ->
            if (visited.add(superclass)) queue += superclass to distance + 1
        }
        current.interfaces.forEach { iface ->
            if (visited.add(iface)) queue += iface to distance + 1
        }
    }
    return Int.MAX_VALUE
}
