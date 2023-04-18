# Haystack Interaction

An reproduction of the odd haystack interaction that can happen when using custom executors.

Note that you need OpenJDK 19 to test the issue.

## Final Conclusion

So, after a day of debugging it looks like this is most likely an issue with
Loom more than anything. Calling `set-agent-send-off-executor!` will cause
`pmap` to use that executor. Deeply nested spawning of threads seems to lock up loom easily (ie.
a few level of pmap nesting). Below is a code snippet that can be used to see it:


```clojure
(defn ppmap
  [f coll]
  (->> coll
       (mapv
         (fn [x]
           (let [cf (java.util.concurrent.CompletableFuture.)]
             (Thread/startVirtualThread
               (bound-fn []
                 (try
                   (.complete cf (f x))
                   (catch Exception e
                     (.completeExceptionally cf e)))))
             cf)))
       (map deref)))

(let [n 4]
   (->> (range n)
        (ppmap
          (fn [_]
            (->> (range n)
                 (ppmap (fn [_]
                          (->> (range n)
                               (ppmap (fn [_]
                                        (ppmap identity (range n))))
                               (apply concat)
                               (into #{}))))
                 (apply concat)
                 (into #{}))))
        (apply concat)
        (into #{})))
```


## Instructions

Start the nrepl server using the following command:

```
/usr/bin/clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.0.0"} cider/cider-nrepl {:mvn/version "0.30.0"}} :aliases {:cider/nrepl {:main-opts ["-m" "nrepl.cmdline" "--middleware" "[cider.nrepl/cider-middleware]"]}}}' -M:repro:cider/nrepl
```

Open `src/repro.clj` and evaluate the buffer.


Evaluate the content of the comment block to trigger a compilation error and bask in the glory of a
a hung nrepl session.


## My Best Guess

After a lot of debugging, I have narrowed it down to an interaction between `delay`, `pmap` and changing the `send-off` executor (which is used by `pmap`) to the `(Executors/newVirtualThreadPerTaskExecutor)` (a JDK 19 preview feature w/ Loom).

This interaction can be seen with [the use of pmap inside analyze-stacktrace-data](https://github.com/clojure-emacs/haystack/blob/master/src/haystack/analyzer.clj#L319) and the delay used by [ns-common-prefix](https://github.com/clojure-emacs/haystack/blob/master/src/haystack/analyzer.clj#L188) in haystack.

~~I suspect it might be some weird interaction with haystack being an inlined dependency.~~ Edit:
I tried switching haystack to not be an inlined dependency and the issue was still hit, so it doesn't matter whether it's inlined or not.
