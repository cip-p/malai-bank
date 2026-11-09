package com.malai.seeder.commands

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import picocli.CommandLine.Command

/** Convenience pipeline: `clean → migrate → seed`. Mirrors `bin/demo-reset.sh`. */
@Component
@Command(name = "reset", description = ["Convenience: clean + migrate + seed."])
class ResetCommand(
    private val clean: CleanCommand,
    private val migrate: MigrateCommand,
    private val seed: SeedCommand,
) : Runnable {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run() {
        log.info("reset: clean + migrate + seed")
        clean.run()
        migrate.run()
        seed.run()
        log.info("reset: complete")
    }
}
