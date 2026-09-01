package com.nutomic.syncthingandroid.superuser;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import com.nutomic.syncthingandroid.superuser.SuperuserBooleanResult;
import com.nutomic.syncthingandroid.superuser.SuperuserCoreExitResult;
import com.nutomic.syncthingandroid.superuser.SuperuserCoreStatus;
import com.nutomic.syncthingandroid.superuser.SuperuserOperationResult;
import com.nutomic.syncthingandroid.superuser.SuperuserStateFileResult;
import com.nutomic.syncthingandroid.superuser.SuperuserStringListResult;
import com.nutomic.syncthingandroid.superuser.SuperuserUidResult;

interface ISyncthingSuperuserService {
    SuperuserUidResult verifySuperuser();
    SuperuserOperationResult recoverOrphanedCore();
    SuperuserOperationResult startCore(int commandId, in Bundle environment, boolean captureStdout);
    SuperuserCoreExitResult waitForCoreExit();
    SuperuserOperationResult stopOwnedCore();
    SuperuserCoreStatus getCoreStatus();

    SuperuserBooleanResult testFolderWritable(String absolutePath);
    SuperuserStringListResult findSyncConflicts(String absoluteConfiguredFolderPath);

    SuperuserStateFileResult openStateFileForRead(int stateFileId);
    SuperuserOperationResult writeStateFileAtomic(int stateFileId, in ParcelFileDescriptor source);
    SuperuserOperationResult deleteStateFile(int stateFileId);
    SuperuserOperationResult stageBackupState(String transferId, int appUid, int appGid);
    SuperuserOperationResult installBackupState(String transferId);
    SuperuserOperationResult repairAppPrivateState(int appUid, int appGid);
}
