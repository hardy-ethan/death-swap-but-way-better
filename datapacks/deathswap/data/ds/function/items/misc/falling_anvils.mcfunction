fill ~ ~ ~ ~ ~1 ~ air
fill ~1 ~2 ~1 ~-1 ~2 ~-1 air
fill ~2 ~3 ~2 ~-2 ~3 ~-2 air
fill ~3 ~4 ~3 ~-3 ~8 ~-3 air
fill ~3 ~6 ~3 ~-3 ~8 ~-3 anvil

execute if score Lang Core matches 1 run title @s title ">> HEADS UP!! <<"
execute if score Lang Core matches 2 run title @s title ">> 小心!! <<"
