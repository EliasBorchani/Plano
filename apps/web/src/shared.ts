// The only file that knows how the Kotlin layer is shaped: screens import from here.
import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'
import type { Controller, KtList } from 'plano-shared'

export * from 'plano-shared'

/**
 * Kotlin lists cross as KtList; screens want plain arrays. The view Kotlin offers is a Proxy that
 * breaks `'length' in view` (some libraries and test matchers do that), so this copies it.
 */
export function arr<T>(list: KtList<T>): readonly T[] {
  return Array.from(list.asJsReadonlyArrayView())
}

/**
 * Creates a Kotlin controller for the component's lifetime and re-renders on each new state.
 * Created in an effect (not during render) so StrictMode's mount, unmount, mount gives one live controller.
 * Returns null on the first render.
 */
export function useController<C extends Controller<unknown>>(create: () => C, deps: readonly unknown[]): [C['state'] | null, C | null] {
  const [controller, setController] = useState<C | null>(null)

  useEffect(() => {
    const created = create()
    setController(created)
    return () => created.dispose()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  const subscribe = useCallback((onChange: () => void) => controller?.subscribe(onChange) ?? (() => {}), [controller])
  const state = useSyncExternalStore(subscribe, () => (controller?.state ?? null) as C['state'] | null)
  return [state, controller]
}

/** For app-wide controllers that live as long as the page. */
export function useControllerState<S>(controller: Controller<S>): S {
  const subscribe = useCallback((onChange: () => void) => controller.subscribe(onChange), [controller])
  return useSyncExternalStore(subscribe, () => controller.state)
}
