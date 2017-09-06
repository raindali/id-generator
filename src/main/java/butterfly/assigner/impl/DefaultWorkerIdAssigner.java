package butterfly.assigner.impl;

import butterfly.assigner.WorkerIdAssigner;

/**
 * 人工指定workerId
 * @author Ricky Fung
 */
public class DefaultWorkerIdAssigner implements WorkerIdAssigner {

    private final long id;

    public DefaultWorkerIdAssigner(long id) {
        this.id = id;
    }

    @Override
    public long getWorkId() {
        return id;
    }
}