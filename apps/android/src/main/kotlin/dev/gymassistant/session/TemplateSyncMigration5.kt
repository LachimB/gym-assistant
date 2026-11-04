package dev.gymassistant.session

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only. Existing records and their storage remain unchanged. */
val TEMPLATE_SYNC_MIGRATION_4_5:Migration=object:Migration(4,5) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `project_storage_roots` (`rootBindingId` TEXT NOT NULL, `provider` TEXT NOT NULL, `permissionId` TEXT NOT NULL, `commonRootFolderId` TEXT NOT NULL, `marker` TEXT NOT NULL, `state` TEXT NOT NULL, `verifiedAt` TEXT, PRIMARY KEY(`rootBindingId`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_storage_roots_provider_permissionId` ON `project_storage_roots` (`provider`, `permissionId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `template_sync_bindings` (`bindingId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `rootBindingId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `templateFolderId` TEXT NOT NULL, `workingFolderId` TEXT NOT NULL, `historyFolderId` TEXT NOT NULL, `metadataFolderId` TEXT NOT NULL, `marker` TEXT NOT NULL, `headFileId` TEXT NOT NULL, `lastRemoteHeadTag` TEXT, `lastRemoteGeneration` TEXT, `baseRevisionId` TEXT, `baseSourceHash` TEXT, `enabled` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`bindingId`), FOREIGN KEY(`connectionId`) REFERENCES `drive_connections`(`connectionId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`rootBindingId`) REFERENCES `project_storage_roots`(`rootBindingId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`templateId`) REFERENCES `template_heads`(`templateId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_template_sync_bindings_connectionId_templateId` ON `template_sync_bindings` (`connectionId`, `templateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_sync_bindings_rootBindingId` ON `template_sync_bindings` (`rootBindingId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_sync_bindings_templateId` ON `template_sync_bindings` (`templateId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `template_sync_jobs` (`jobId` TEXT NOT NULL, `bindingId` TEXT NOT NULL, `operationId` TEXT NOT NULL, `direction` TEXT NOT NULL, `expectedLocalRevisionId` TEXT NOT NULL, `expectedRemoteGeneration` TEXT, `expectedRemoteHeadTag` TEXT, `sourceHash` TEXT NOT NULL, `compiledPlanHash` TEXT NOT NULL, `intentPath` TEXT NOT NULL, `intentHash` TEXT NOT NULL, `reservedObjectIdsJson` TEXT NOT NULL, `state` TEXT NOT NULL, `errorCode` TEXT, `attemptCount` INTEGER NOT NULL, `nextAttemptAt` INTEGER, `leaseOwner` TEXT, `leaseGeneration` INTEGER NOT NULL, `leaseUntil` INTEGER, `leaseBootId` TEXT, PRIMARY KEY(`jobId`), FOREIGN KEY(`bindingId`) REFERENCES `template_sync_bindings`(`bindingId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_template_sync_jobs_operationId` ON `template_sync_jobs` (`operationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_sync_jobs_bindingId` ON `template_sync_jobs` (`bindingId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `template_sync_observations` (`observationId` TEXT NOT NULL, `bindingId` TEXT NOT NULL, `candidateId` TEXT, `observationPath` TEXT NOT NULL, `observationHash` TEXT NOT NULL, `expectedLocalRevisionId` TEXT NOT NULL, `remoteGeneration` TEXT, `remoteHeadTag` TEXT, `validationCode` TEXT NOT NULL, `reviewState` TEXT NOT NULL, `capturedAt` TEXT NOT NULL, PRIMARY KEY(`observationId`), FOREIGN KEY(`bindingId`) REFERENCES `template_sync_bindings`(`bindingId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`candidateId`) REFERENCES `template_candidates`(`candidateId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_sync_observations_bindingId` ON `template_sync_observations` (`bindingId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_sync_observations_candidateId` ON `template_sync_observations` (`candidateId`)")
    }
}
