import { HttpErrorResponse } from '@angular/common/http';

/** Shown whenever the browser could not reach the gateway at all. */
export const OFFLINE_MESSAGE = 'Cannot reach the server. Check your connection.';

/**
 * Per-status messages plus the message to use when no status matches.
 * Statuses the caller does not list fall through to `fallback`, except a
 * status of 0, which always reports the connection failure.
 */
export interface HttpErrorMessages {
  [status: number]: string | undefined;
  fallback: string;
}

/**
 * The `message` field the services put in their error bodies, when present.
 * Used where the server's wording is more specific than anything the client
 * could invent, such as which product ran out of stock.
 */
export function serverMessage(error: unknown): string | null {
  if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
    return error.error.message;
  }
  return null;
}

/** Maps a failed request to the message a user should see. */
export function httpErrorMessage(error: unknown, messages: HttpErrorMessages): string {
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  const specific = messages[status];
  if (specific) return specific;
  if (status === 0) return OFFLINE_MESSAGE;
  return messages.fallback;
}

/**
 * Message for a failed "add to cart", shared by the catalog card and the
 * product detail page so both report stock conflicts identically.
 */
export function addToCartErrorMessage(error: unknown): string {
  return httpErrorMessage(error, {
    409: serverMessage(error) ?? 'Not enough stock available for that quantity.',
    404: 'This product is no longer available.',
    fallback: 'Could not add this item to your cart. Try again.',
  });
}
