import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Root } from './Root'
import { CreateEventScreen } from './screens/CreateEventScreen'
import { EventListScreen } from './screens/EventListScreen'
import { EventScreen } from './screens/EventScreen'
import { SessionContext } from './session'
import { sessionController, startPlano } from './shared'
import './app.css'

// The shared Kotlin layer: started once, before the first render.
startPlano(`${location.origin}/api/`)
const session = sessionController()

const router = createBrowserRouter([
  {
    element: <Root />,
    children: [
      { index: true, element: <EventListScreen /> },
      { path: 'new', element: <CreateEventScreen /> },
      { path: 'e/:eventId', element: <EventScreen /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionContext value={session}>
      <RouterProvider router={router} />
    </SessionContext>
  </StrictMode>,
)
