title @a title " "
title @a subtitle " "
execute if score Lang Core matches 1 run title @a actionbar {"text":">> Swapped! <<","color":"gold"}
execute if score Lang Core matches 2 run title @a actionbar {"text":">> 球员互换! <<","color":"gold"}

##execute as @a run attribute @s minecraft:fall_damage_multiplier base set 0.0
gamerule fall_damage false
schedule function ds:game/end_fall_dam_prot 1t

execute as @a[tag=playing] run trigger tp_away set 0
schedule function ds:game/give_back_tp 12s
execute as @a[tag=96spying] run trigger 96spying set 1
execute as @a[tag=96spying] run function ds:items/misc/end_96spying
execute as @a at @s run forceload add ~ ~
execute as @p[tag=playing,scores={PNo=1}] at @s run summon marker ~ ~ ~ {Tags:["ent","PNo1Loc"]}
tp @a[tag=playing,scores={PNo=1}] @p[tag=playing,scores={PNo=2}]
tp @a[tag=playing,scores={PNo=2}] @p[tag=playing,scores={PNo=3}]
tp @a[tag=playing,scores={PNo=3}] @p[tag=playing,scores={PNo=4}]
tp @a[tag=playing,scores={PNo=4}] @p[tag=playing,scores={PNo=5}]
tp @a[tag=playing,scores={PNo=5}] @p[tag=playing,scores={PNo=6}]
tp @a[tag=playing,scores={PNo=6}] @p[tag=playing,scores={PNo=7}]
tp @a[tag=playing,scores={PNo=7}] @p[tag=playing,scores={PNo=8}]
tp @a[tag=playing,scores={PNo=8}] @p[tag=playing,scores={PNo=9}]
tp @a[tag=playing,scores={PNo=9}] @p[tag=playing,scores={PNo=10}]
tp @a[tag=playing,scores={PNo=10}] @p[tag=playing,scores={PNo=11}]
tp @a[tag=playing,scores={PNo=11}] @p[tag=playing,scores={PNo=12}]

execute if score Players Core matches 1 run tp @a[tag=playing,scores={PNo=1}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 2 run tp @a[tag=playing,scores={PNo=2}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 3 run tp @a[tag=playing,scores={PNo=3}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 4 run tp @a[tag=playing,scores={PNo=4}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 5 run tp @a[tag=playing,scores={PNo=5}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 6 run tp @a[tag=playing,scores={PNo=6}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 7 run tp @a[tag=playing,scores={PNo=7}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 8 run tp @a[tag=playing,scores={PNo=8}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 9 run tp @a[tag=playing,scores={PNo=9}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 10 run tp @a[tag=playing,scores={PNo=10}] @e[limit=1,tag=PNo1Loc]
execute if score Players Core matches 11 run tp @a[tag=playing,scores={PNo=11}] @e[limit=1,tag=PNo1Loc]
tp @a[tag=playing,scores={PNo=12..}] @e[limit=1,tag=PNo1Loc]

execute if score Players Core matches 2.. run tellraw @a[scores={PNo=1}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=2}]","bold":false}]
execute if score Players Core matches 3.. run tellraw @a[scores={PNo=2}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=3}]","bold":false}]
execute if score Players Core matches 4.. run tellraw @a[scores={PNo=3}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=4}]","bold":false}]
execute if score Players Core matches 5.. run tellraw @a[scores={PNo=4}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=5}]","bold":false}]
execute if score Players Core matches 6.. run tellraw @a[scores={PNo=5}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=6}]","bold":false}]
execute if score Players Core matches 7.. run tellraw @a[scores={PNo=6}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=7}]","bold":false}]
execute if score Players Core matches 8.. run tellraw @a[scores={PNo=7}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=8}]","bold":false}]
execute if score Players Core matches 9.. run tellraw @a[scores={PNo=8}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=9}]","bold":false}]
execute if score Players Core matches 10.. run tellraw @a[scores={PNo=9}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=10}]","bold":false}]
execute if score Players Core matches 11.. run tellraw @a[scores={PNo=10}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=11}]","bold":false}]
execute if score Players Core matches 12.. run tellraw @a[scores={PNo=11}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=12}]","bold":false}]

execute if score Players Core matches ..1 run tellraw @a[scores={PNo=1}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..2 run tellraw @a[scores={PNo=2}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..3 run tellraw @a[scores={PNo=3}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..4 run tellraw @a[scores={PNo=4}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..5 run tellraw @a[scores={PNo=5}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..6 run tellraw @a[scores={PNo=6}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..7 run tellraw @a[scores={PNo=7}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..8 run tellraw @a[scores={PNo=8}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..9 run tellraw @a[scores={PNo=9}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..10 run tellraw @a[scores={PNo=10}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..11 run tellraw @a[scores={PNo=11}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]
execute if score Players Core matches ..12 run tellraw @a[scores={PNo=12}] [{"text":"* You warped to: ","color":"green","bold":true},{"selector":"@p[scores={PNo=1}]","bold":false}]

execute if score Lang Core matches 2 run tellraw @a[tag=playing] {"text":"* 你已传送到该玩家所在位置 ^^","color":"green","bold":false}

execute if entity Jerriess unless entity @e[tag=PNo1Loc] run say DEBUG: PNo1Loc DID NOT SPAWN
schedule function ds:extra/clear_prev 3t