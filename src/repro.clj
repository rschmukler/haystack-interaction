(ns repro)

(set-agent-send-off-executor!
  (java.util.concurrent.Executors/newVirtualThreadPerTaskExecutor))


(comment
  (defn foo
    []
    (bar)))
