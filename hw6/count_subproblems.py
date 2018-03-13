visited_problems = []

def count(players):
    if sorted(players) in visited_problems:
        return 0
    else:
        visited_problems.append(sorted(players))
        print(players)
        total = 0
        if len(players) == 1:
            return 1
        for pos in list(set(players)):
            sublist = players[:]
            sublist.remove(pos)
            total = total + count(sublist)
        return total

#print(count(['QB', 'RB', 'RB', 'WR', 'WR', 'WR', 'TE', 'K', 'DEF']))
count(['QB', 'RB', 'RB', 'WR', 'WR', 'WR', 'TE', 'K', 'DEF'])
for p in sorted(visited_problems):
    print(p, 'len:', len(p))
print('Total visited (sub)problems: {}'.format(len(visited_problems)))