package com.mauro0805.forceblend.bukkit;

record WorkerBudget(int heapMiB, int threads) {
    static final String HEAP_PROPERTY = "forceblend.workerHeapMiB";

    static WorkerBudget configured(int requestedThreads, int processors) {
        String value = System.getProperty(HEAP_PROPERTY, "512");
        final int heap;
        try {
            heap = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(HEAP_PROPERTY + " must be an integer from 256 to 4096", exception);
        }
        if (heap < 256 || heap > 4096) {
            throw new IllegalArgumentException(HEAP_PROPERTY + " must be between 256 and 4096");
        }
        return forHeap(requestedThreads, processors, heap);
    }

    static WorkerBudget forHeap(int requestedThreads, int processors, int heapMiB) {
        if (requestedThreads < 0 || processors < 1 || heapMiB < 1) {
            throw new IllegalArgumentException("Invalid worker resource budget");
        }
        int requested = requestedThreads == 0 ? processors : Math.min(requestedThreads, processors);
        int memoryLimit = Math.max(1, (heapMiB - 256) / 128);
        return new WorkerBudget(heapMiB, Math.min(requested, memoryLimit));
    }
}
