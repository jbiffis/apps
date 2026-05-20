// Helpers for walking the /event-types tree.

/** All loggable leaves (non-category) anywhere in the tree, in tree order. */
export function flattenLeaves(tree = []) {
  const out = []
  const walk = (n) => {
    if (n.isCategory) (n.children || []).forEach(walk)
    else out.push(n)
  }
  tree.forEach(walk)
  return out
}

/** Leaves beneath a single node (or the node itself if it's a leaf). */
export function leavesUnder(node) {
  if (!node) return []
  if (!node.isCategory) return [node]
  return flattenLeaves(node.children || [])
}
