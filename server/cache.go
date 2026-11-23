package main

import (
	"sync"
	"time"
)

// Cache is a TTL cache with in-flight deduplication (N concurrent misses → 1 upstream call)
// and serve-stale-on-error, so an upstream hiccup or exhausted quota degrades gracefully.
type Cache struct {
	mu       sync.Mutex
	entries  map[string]*cacheEntry
	inflight map[string]*cacheCall
	max      int
}

type cacheEntry struct {
	val any
	exp time.Time
}

type cacheCall struct {
	done chan struct{}
	val  any
	err  error
}

func NewCache(max int) *Cache {
	return &Cache{entries: map[string]*cacheEntry{}, inflight: map[string]*cacheCall{}, max: max}
}

func (c *Cache) Len() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.entries)
}

func (c *Cache) get(key string, ttl time.Duration, fn func() (any, error)) (any, error) {
	c.mu.Lock()
	e := c.entries[key]
	if e != nil && time.Now().Before(e.exp) {
		c.mu.Unlock()
		return e.val, nil
	}
	if call := c.inflight[key]; call != nil {
		c.mu.Unlock()
		<-call.done
		return call.val, call.err
	}
	call := &cacheCall{done: make(chan struct{})}
	c.inflight[key] = call
	c.mu.Unlock()

	call.val, call.err = fn()

	c.mu.Lock()
	switch {
	case call.err == nil:
		if len(c.entries) >= c.max {
			// shortcut: evicts an arbitrary entry (Go map order), fine at this size; use an LRU if hit rate matters.
			for k := range c.entries {
				delete(c.entries, k)
				break
			}
		}
		c.entries[key] = &cacheEntry{val: call.val, exp: time.Now().Add(ttl)}
	case e != nil: // stale beats an error
		call.val, call.err = e.val, nil
		e.exp = time.Now().Add(30 * time.Second)
	}
	delete(c.inflight, key)
	c.mu.Unlock()
	close(call.done)
	return call.val, call.err
}

func cached[T any](c *Cache, key string, ttl time.Duration, fn func() (T, error)) (T, error) {
	v, err := c.get(key, ttl, func() (any, error) { return fn() })
	if err != nil {
		var zero T
		return zero, err
	}
	return v.(T), nil
}
