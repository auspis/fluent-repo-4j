#!/bin/bash

# Script to delete local branches that have been deleted remotely
# Keeps local-only branches that don't track any remote

set -e

echo "Fetching latest changes from remote..."
git fetch --prune

echo "Identifying local branches that track deleted remote branches..."
gone_branches=$(git branch -vv | grep ': gone]' | awk '{print $1}')

if [ -z "$gone_branches" ]; then
    echo "No local branches found that track deleted remote branches."
    exit 0
fi

echo "The following branches will be deleted:"
echo "$gone_branches"
echo

read -p "Do you want to proceed? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]([Ee][Ss])?$ ]]; then
    echo "Operation cancelled."
    exit 0
fi

echo "$gone_branches" | xargs git branch -D

echo "Cleanup completed."