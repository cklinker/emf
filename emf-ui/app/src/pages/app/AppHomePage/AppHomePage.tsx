/**
 * AppHomePage
 *
 * End-user home/dashboard page. Shows a welcome message,
 * quick actions, recent items, and favorites.
 */

import React from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Plus, Clock, Star, ArrowRight } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { useAppContext } from '@/context/AppContext'

export function AppHomePage(): React.ReactElement {
  const { tenantSlug } = useParams<{ tenantSlug: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { recentItems, favorites } = useAppContext()

  const basePath = `/${tenantSlug}/app`

  // Determine greeting based on time of day
  const hour = new Date().getHours()
  let greeting = 'Good morning'
  if (hour >= 12 && hour < 17) greeting = 'Good afternoon'
  if (hour >= 17) greeting = 'Good evening'

  const firstName = user?.name?.split(' ')[0] || 'there'

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      {/* Welcome header */}
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          {greeting}, {firstName}
        </h1>
        <p className="text-sm text-muted-foreground">
          Here&apos;s an overview of your recent activity.
        </p>
      </div>

      {/* Quick actions */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Quick Actions</CardTitle>
          <CardDescription>Frequently used actions</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" disabled>
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              New Record
            </Button>
          </div>
          <p className="mt-3 text-xs text-muted-foreground">
            Quick actions will appear here once collections are configured.
          </p>
        </CardContent>
      </Card>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Recent items */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Clock className="h-4 w-4 text-muted-foreground" />
              Recent Items
            </CardTitle>
          </CardHeader>
          <CardContent>
            {recentItems.length === 0 ? (
              <p className="py-4 text-center text-sm text-muted-foreground">
                No recent items yet. Records you view will appear here.
              </p>
            ) : (
              <div className="space-y-2">
                {recentItems.slice(0, 5).map((item) => (
                  <button
                    key={`${item.collectionName}-${item.id}`}
                    onClick={() => navigate(`${basePath}/o/${item.collectionName}/${item.id}`)}
                    className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-accent"
                  >
                    <span className="flex-1 truncate font-medium text-foreground">
                      {item.label}
                    </span>
                    <span className="text-xs text-muted-foreground">{item.collectionName}</span>
                    <ArrowRight className="h-3.5 w-3.5 text-muted-foreground" />
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Favorites */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Star className="h-4 w-4 text-muted-foreground" />
              Favorites
            </CardTitle>
          </CardHeader>
          <CardContent>
            {favorites.length === 0 ? (
              <p className="py-4 text-center text-sm text-muted-foreground">
                No favorites yet. Star records or collections to access them quickly.
              </p>
            ) : (
              <div className="space-y-2">
                {favorites.slice(0, 5).map((fav) => (
                  <button
                    key={fav.key}
                    onClick={() => {
                      if (fav.type === 'record' && fav.recordId) {
                        navigate(`${basePath}/o/${fav.collectionName}/${fav.recordId}`)
                      } else {
                        navigate(`${basePath}/o/${fav.collectionName}`)
                      }
                    }}
                    className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-accent"
                  >
                    <Star className="h-3.5 w-3.5 fill-yellow-400 text-yellow-400" />
                    <span className="flex-1 truncate font-medium text-foreground">{fav.label}</span>
                    <span className="text-xs text-muted-foreground">{fav.type}</span>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
