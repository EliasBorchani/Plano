// Every user-facing French string. Kotlin sends keys (enum names); labels live here.

export const eventTypes: Record<string, string> = {
  DINNER: 'Dîner',
  LUNCH: 'Déjeuner',
  BRUNCH: 'Brunch',
  DRINKS: 'Apéro',
  PARTY: 'Soirée',
  BIRTHDAY: 'Anniversaire',
  BARBECUE: 'Barbecue',
  PICNIC: 'Pique-nique',
  GAME_NIGHT: 'Soirée jeux',
  MOVIE_NIGHT: 'Soirée film',
  CONCERT: 'Concert',
  FESTIVAL: 'Festival',
  SHOW: 'Spectacle',
  EXHIBITION: 'Exposition',
  SPORTS_SESSION: 'Séance de sport',
  HIKE: 'Randonnée',
  OUTDOOR_DAY: 'Journée plein air',
  DAY_TRIP: 'Excursion',
  WEEKEND_GETAWAY: 'Week-end',
  HOLIDAY_TRIP: 'Vacances',
  SKI_TRIP: 'Séjour au ski',
  ROAD_TRIP: 'Road trip',
  BACHELOR_PARTY: 'Enterrement de vie de garçon',
  BACHELORETTE_PARTY: 'Enterrement de vie de jeune fille',
  WEDDING: 'Mariage',
  CELEBRATION: 'Fête',
  OTHER: 'Autre',
}

export const roles: Record<string, string> = {
  OWNER: 'Organisateur',
  PLANNER: 'Co-organisateur',
  PARTICIPANT: 'Participant',
}

export const choices: Record<string, { label: string; short: string }> = {
  PREFER: { label: 'Je préfère', short: 'Oui' },
  IF_MAJORITY: { label: 'Seulement si la majorité', short: 'Si majorité' },
  CANNOT: { label: 'Je ne peux pas', short: 'Non' },
}

export const errors: Record<string, string> = {
  // AppError.Kind
  UNAUTHORIZED: 'Connexion refusée. Réessaie.',
  FORBIDDEN: "Tu n'as pas le droit de faire ça.",
  NOT_FOUND: 'Introuvable.',
  CONFLICT: 'Déjà fait.',
  INVALID: 'Valeur invalide.',
  NETWORK: 'Pas de connexion au serveur.',
  SERVER: 'Le serveur a rencontré un problème.',
  // NewEventError
  TITLE_MISSING: 'Donne un titre à ton événement.',
  TITLE_TOO_LONG: 'Le titre est trop long (80 caractères au plus).',
  DESCRIPTION_TOO_LONG: 'La description est trop longue.',
  // GuestNameError
  NAME_MISSING: 'Écris ton prénom.',
  NAME_TOO_LONG: 'Ce nom est trop long (40 caractères au plus).',
  NAME_TAKEN: 'Quelqu’un porte déjà ce nom ici. Si c’est toi, choisis « C’est moi » plus bas ; sinon ajoute une initiale.',
  // AddDateError
  DATE_INVALID: 'Choisis une date.',
  DATE_IN_PAST: 'Cette date est passée.',
  DATE_ALREADY_OFFERED: 'Cette date est déjà proposée.',
}

export function errorText(key: string | null | undefined): string | null {
  if (!key) return null
  return errors[key] ?? errors.SERVER
}

export const t = {
  appName: 'Plano',
  tagline: 'Organisez une sortie entre amis, depuis un seul lien.',
  continueWithApple: 'Continuer avec Apple',
  devApple: 'Apple (développement)',
  devAppleHint: "Sign in with Apple n'est pas encore configuré : un nom suffit pour tester.",
  yourName: 'Ton prénom',
  signIn: 'Se connecter',
  noProvider: "Aucune méthode de connexion n'est configurée (voir .env.example).",
  signOut: 'Se déconnecter',
  myEvents: 'Mes événements',
  noEvents: "Pas encore d'événement. Crée le premier et partage le lien dans ton groupe.",
  newEvent: 'Nouvel événement',
  back: 'Retour',
  title: 'Titre',
  titlePlaceholder: 'Dîner chez Léa',
  type: 'Type',
  description: 'Description',
  descriptionPlaceholder: 'Optionnel : ce qu’il faut savoir.',
  create: "Créer l'événement",
  creating: 'Création…',
  loading: 'Chargement…',
  retry: 'Réessayer',
  notFound: "Cet événement n'existe pas, ou tu n'y as pas accès.",
  whenTitle: 'Quelle date ?',
  noDates: 'Aucune date proposée pour l’instant.',
  noDatesParticipant: 'Les organisateurs vont proposer des dates.',
  addDate: 'Proposer',
  newDateLabel: 'Proposer une date',
  leading: 'En tête',
  leadingOnPrefer: 'En tête des oui',
  leadingWithIfMajority: 'En tête avec les si majorité',
  people: 'Participants',
  you: 'toi',
  share: 'Partager le lien',
  copied: 'Lien copié',
  shareText: (title: string) => `On s'organise pour « ${title} » ici :`,
  guestIntro: 'Ton prénom suffit pour voter. Pas besoin de compte.',
  guestJoin: 'Participer',
  guestAlreadyHere: 'Déjà venu sur un autre appareil ?',
  itsMe: (name: string) => `C’est moi : ${name}`,
  haveAccount: 'J’ai un compte Google ou Apple',
  guestElsewhere: (name: string) => `Tu es invité ailleurs sous le nom ${name}. Rejoindre cet événement te déconnecte de l’autre (tu pourras y revenir avec « C’est moi »).`,
  guestSignOutWarning:
    'Tu n’as pas de compte : sur cet appareil, tes votes ne seront plus liés à toi. Tu pourras revenir avec « C’est moi » depuis le lien de l’événement. Se déconnecter ?',
  keepVotesTitle: 'Garde tes votes',
  keepVotesText: 'Connecte-toi pour retrouver cet événement sur tous tes appareils. Tes votes sont conservés.',
  votes: (n: number) => (n === 0 ? 'aucun vote' : n === 1 ? '1 vote' : `${n} votes`),
}
