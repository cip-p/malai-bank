package com.malai.seeder

import com.malai.seeder.commands.CleanCommand
import com.malai.seeder.commands.MigrateCommand
import com.malai.seeder.commands.ResetCommand
import com.malai.seeder.commands.SeedCommand
import com.malai.seeder.commands.SeedUserCommand
import com.malai.seeder.commands.VerifyChainCommand
import picocli.CommandLine.Command
import picocli.CommandLine.HelpCommand

// Root Picocli command. Bare invocation prints usage; real work happens in the
// `clean / migrate / seed / reset / verify-chain / seed-user` subcommands. They
// are subcommands, not options, so they take no leading dashes.
@Command(
    name = "malai-seeder",
    mixinStandardHelpOptions = true,
    description = ["Resets a Malai environment to the canonical demo dataset (Plan 02-15)."],
    subcommands = [
        CleanCommand::class,
        MigrateCommand::class,
        SeedCommand::class,
        ResetCommand::class,
        VerifyChainCommand::class,
        SeedUserCommand::class,
        HelpCommand::class,
    ],
)
class SeederCommand : Runnable {
    override fun run() {
        // Picocli prints the synopsis automatically when no subcommand is given.
    }
}
