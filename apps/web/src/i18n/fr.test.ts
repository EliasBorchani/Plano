import { describe, expect, it } from 'vitest'
import { arr, createEventController, startPlano } from '../shared'
import { choices, eventTypes } from './fr'

// Runs the real Kotlin layer: the lists come from the shared enums, so a new type or choice
// added in Kotlin fails here until it gets a French label.
describe('French labels', () => {
  startPlano('http://localhost/api/')

  it('cover every event type and vote choice', () => {
    const controller = createEventController()
    const types = arr(controller.state.types)
    expect(types).toHaveLength(27)
    expect(types.filter((type) => !eventTypes[type])).toEqual([])
    controller.dispose()
  })

  it('have a label for each vote choice', () => {
    expect(Object.keys(choices).sort()).toEqual(['CANNOT', 'IF_MAJORITY', 'PREFER'])
  })
})
