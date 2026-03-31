package yangfentuozi.batteryrecorder.server;

import yangfentuozi.batteryrecorder.server.recorder.IRecordListener;
import yangfentuozi.batteryrecorder.shared.config.dataclass.ServerSettings;
import yangfentuozi.batteryrecorder.shared.data.RecordsFile;

interface IService {
    void stopService() = 1;
    int getVersion() = 2;

    RecordsFile getCurrRecordsFile() = 10;

    void registerRecordListener(IRecordListener listener) = 100;
    void unregisterRecordListener(IRecordListener listener) = 101;

    void updateConfig(in ServerSettings config) = 200;

    ParcelFileDescriptor sync() = 300;
}
