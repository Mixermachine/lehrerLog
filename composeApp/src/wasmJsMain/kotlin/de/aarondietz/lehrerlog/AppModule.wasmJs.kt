package de.aarondietz.lehrerlog

import org.koin.dsl.module
import org.koin.core.module.Module

actual val platformModule: Module = module {
    // wasmJS-specific bindings (usually empty or minimal)
}
