package com.malai.test.acceptance.kensa

import dev.kensa.Action
import dev.kensa.GivensContext

/**
 * An action that can be used in two ways:
 *
 * 1. Via Kensa's BDD DSL — `given(someAction)` / `and(someAction)` — which invokes the
 *    [dev.kensa.Action.execute] overload that takes a [dev.kensa.GivensContext].
 * 2. Directly from a plain `@SpringTest` class that does not use Kensa, via the no-arg
 *    [execute] overload, e.g. `aUserExists(...).execute()`.
 *
 * Both overloads run the same [block]; the [dev.kensa.GivensContext] is ignored because our fixtures
 * never read it.
 */
class CallableAction(private val block: () -> Unit) : Action<GivensContext> {

    override fun execute(context: GivensContext) = block()

    fun execute() = block()
}
