# Mega speed (1)
execute as @a[tag=playing] if score @s mega_speed matches 1.. run scoreboard players remove @s mega_speed 5
execute as @a[tag=playing] if score @s mega_speed matches 1..5 run function ds:items/misc/remove_speed

# Shield (3 & 31 & 97)
execute as @a[tag=playing,tag=Shield,tag=!game_crashed] if score @s Shield matches 1.. run scoreboard players remove @s Shield 5
execute as @a[tag=playing,tag=Shield,tag=!game_crashed] if score @s Shield matches ..5 run function ds:items/misc/remove_shield

# 100 villagers (8)
execute as @a[tag=playing,tag=100villagers] at @s if score @s 100villagers matches 1.. run function ds:items/misc/100villagers
execute as @a[tag=playing,tag=100villagers] at @s if score @s 100villagers matches 1.. run scoreboard players remove @s 100villagers 1
execute as @a[tag=playing,tag=100villagers] at @s if score @s 100villagers matches ..0 run tag @s remove 100villagers

# Gravel tower (9)
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 19 run function ds:items/misc/gravel_up
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 16 run function ds:items/misc/gravel_up
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 13 run function ds:items/misc/gravel_up
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 10 run function ds:items/misc/gravel_up
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 7 run function ds:items/misc/gravel_up
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 4 run function ds:items/misc/gravel_up
execute if entity @e[tag=gravel_tower] if score TimeT Core matches 1 run function ds:items/misc/gravel_up

# No jumping (??)
execute as @a[tag=playing,tag=jump_disabled] if score @s jump_disabled matches 1.. run scoreboard players remove @s jump_disabled 5
execute as @a[tag=playing,tag=jump_disabled] if score @s jump_disabled matches ..5 run function ds:items/misc/remove_no_jump

# Creative mode (18)
execute as @a[tag=playing,tag=in_creativeMode] run clear @s minecraft:command_block
execute as @a[tag=playing,tag=in_creativeMode] run clear @s minecraft:command_block_minecart
execute as @a[tag=playing,tag=in_creativeMode] run clear @s minecraft:repeating_command_block
execute as @a[tag=playing,tag=in_creativeMode] run clear @s minecraft:chain_command_block
execute as @a[tag=playing,tag=in_creativeMode] run clear @s minecraft:enchanted_golden_apple
execute as @a[tag=playing,tag=in_creativeMode] if score @s creativeMode matches 1.. run scoreboard players remove @s creativeMode 5
execute as @a[tag=playing,tag=in_creativeMode] if score @s creativeMode matches ..5 run function ds:items/misc/exit_creative_mode

# Motion sickness/spinning (21)
execute as @a[tag=playing,tag=motion_sick] at @s if score @s motion_sick matches 1.. run rotate @s ~10 ~0.1
execute as @a[tag=playing,tag=motion_sick] at @s if score @s motion_sick matches 1.. run scoreboard players remove @s motion_sick 5
execute as @a[tag=playing,tag=motion_sick] at @s if score @s motion_sick matches ..5 run function ds:items/misc/done_spinning

# Bedrock trail (25)
execute as @a[tag=playing,tag=bedrock_trail] at @s if score @s bedrock_trail matches 1.. run setblock ~ ~-0.9 ~ minecraft:bedrock
execute as @a[tag=playing,tag=bedrock_trail] at @s if score @s bedrock_trail matches 1.. run scoreboard players remove @s bedrock_trail 5
execute as @a[tag=playing,tag=bedrock_trail] at @s if score @s bedrock_trail matches ..5 run function ds:items/misc/bedrock_trail_done

# No interaction (33)
execute as @a[tag=playing,tag=no_interaction] at @s if score @s no_interaction matches 1.. run scoreboard players remove @s no_interaction 5
execute as @a[tag=playing,tag=no_interaction] at @s if score @s no_interaction matches ..5 run function ds:items/misc/no_interaction_done

# Many bees (36)
execute as @a[tag=playing,tag=many_bees] at @s if score @s many_bees matches 1.. run function ds:items/misc/many_bees
execute as @a[tag=playing,tag=many_bees] at @s if score @s many_bees matches 1.. run scoreboard players remove @s many_bees 1
execute as @a[tag=playing,tag=many_bees] at @s if score @s many_bees matches ..0 run tag @s remove many_bees

# Pumpkin head (41)
execute as @a[tag=playing,tag=pumpkin_head] at @s if score @s pumpkin_head matches 1.. run scoreboard players remove @s pumpkin_head 5
execute as @a[tag=playing,tag=pumpkin_head] at @s if score @s pumpkin_head matches ..5 run function ds:items/misc/pumpkin_head_done

# Tiny scale (43)
execute as @a[tag=playing,tag=tiny_scale] at @s if score @s tiny_scale matches 1.. run scoreboard players remove @s tiny_scale 5
execute as @a[tag=playing,tag=tiny_scale] at @s if score @s tiny_scale matches ..5 run function ds:items/misc/tiny_scale_done

# Huge scale (44)
execute as @a[tag=playing,tag=huge_scale] at @s if score @s huge_scale matches 1.. run scoreboard players remove @s huge_scale 5
execute as @a[tag=playing,tag=huge_scale] at @s if score @s huge_scale matches ..5 run function ds:items/misc/huge_scale_done

# Nether world (50)
execute as @a[tag=playing,tag=nether_world] at @s if score @s nether_world matches 1.. run function ds:items/misc/nether_world
execute as @a[tag=playing,tag=nether_world] at @s if score @s nether_world matches 1.. run scoreboard players remove @s nether_world 5
execute as @a[tag=playing,tag=nether_world] at @s if score @s nether_world matches ..5 run function ds:items/misc/done_nether_world

# Earthquake (55)
execute as @a[tag=playing,tag=55earthquake] at @s if score 55TimeS Items matches 1.. run function ds:items/misc/55earthquake

# Blocked items (56)
execute as @a[tag=playing,tag=blockedItems] at @s if score @s blockedItems matches 1.. run scoreboard players remove @s blockedItems 5
execute as @a[tag=playing,tag=blockedItems] at @s if score @s blockedItems matches ..5 run function ds:items/misc/blockeditems_done

# Look_down (58)
execute as @a[tag=playing,tag=look_down] at @s if score @s look_down matches 1.. run rotate @s ~ ~8
execute as @a[tag=playing,tag=look_down] at @s if score @s look_down matches 1.. run scoreboard players remove @s look_down 5
execute as @a[tag=playing,tag=look_down] at @s if score @s look_down matches ..5 run function ds:items/misc/look_down_done

# Jumpscare (60)
execute as @e[type=parched,tag=jumpscare] at @s if score @s jumpscared matches 1.. run function ds:items/misc/jumpscare
execute as @e[type=parched,tag=jumpscare] at @s if score @s jumpscared matches 1.. run scoreboard players remove @s jumpscared 5
execute as @e[type=parched,tag=jumpscare] at @s if score @s jumpscared matches ..5 run kill @s

# Crashed game rejoin (78)
execute as @a[tag=playing,tag=game_crashed] at @s if score waitForCrashPlayer Core matches 1 if entity @e[type=marker,tag=crash_rejoin,distance=..1.5] run title @s title " "
execute as @a[tag=playing,tag=game_crashed] at @s if score waitForCrashPlayer Core matches 1 if entity @e[type=marker,tag=crash_rejoin,distance=..1.5] run title @s subtitle "Walk 2 blocks away / 步行两个街区"
execute as @a[tag=playing,tag=game_crashed] at @s if score waitForCrashPlayer Core matches 1 unless entity @e[type=marker,tag=crash_rejoin,distance=..1.5] run function ds:items/misc/crash_rejoin

# Spectator mode (80)
execute as @a[tag=playing,tag=specMode] at @s if score @s specMode matches 1.. run scoreboard players remove @s specMode 5
execute as @a[tag=playing,tag=specMode] at @s if score @s specMode matches ..5 run function ds:items/misc/specmode_done

# Mine faster (89)
execute as @a[tag=playing,tag=mine_faster] at @s if score @s mine_faster matches 1.. run scoreboard players remove @s mine_faster 5
execute as @a[tag=playing,tag=mine_faster] at @s if score @s mine_faster matches ..5 run function ds:items/misc/mine_faster_done

# Low gravity (90)
execute if score lowGrav Items matches 1.. run scoreboard players remove lowGrav Items 5
execute if score lowGrav Items matches 1..10 run function ds:items/misc/lowgrav_done

# No fall damage (91)
execute as @a[tag=playing,tag=no_fall_dam] at @s if score @s no_fall_dam matches 1.. run scoreboard players remove @s no_fall_dam 5
execute as @a[tag=playing,tag=no_fall_dam] at @s if score @s no_fall_dam matches ..5 run function ds:items/misc/no_fall_dam_done

# Ears bleed (92)
execute as @a[tag=playing,tag=ears_bleed] at @s if score @s ears_bleed matches 1.. run function ds:items/misc/ears_bleed
execute as @a[tag=playing,tag=ears_bleed] at @s if score @s ears_bleed matches 1.. run scoreboard players remove @s ears_bleed 5
execute as @a[tag=playing,tag=ears_bleed] at @s if score @s ears_bleed matches ..5 run function ds:items/misc/done_ears_bleed

# Spying (96)
execute as @a[tag=playing,tag=96spying] at @s if score @s time96 matches 1.. run scoreboard players remove @s time96 5
execute as @a[tag=playing,tag=96spying] at @s if score @s time96 matches ..0 run trigger 96spying set 1
execute as @a[tag=playing,tag=96spying] at @s unless score @s 96spying matches 0 run function ds:items/misc/end_96spying

# No crafting + smelting (98)
execute as @a[tag=playing,tag=no_craft] at @s if score @s no_craft matches 1.. run function ds:items/misc/no_craft
execute as @a[tag=playing,tag=no_craft] at @s if score @s no_craft matches 1.. run scoreboard players remove @s no_craft 5
execute as @a[tag=playing,tag=no_craft] at @s if score @s no_craft matches ..5 run function ds:items/misc/done_no_craft

# Mob forcefield (99)
execute as @a[tag=playing,tag=mob_forcefield] at @s if score @s mob_forcefield matches 1.. run function ds:items/misc/forcefield
execute as @a[tag=playing,tag=mob_forcefield] at @s if score @s mob_forcefield matches 1.. run scoreboard players remove @s mob_forcefield 5
execute as @a[tag=playing,tag=mob_forcefield] at @s if score @s mob_forcefield matches ..5 run function ds:items/misc/done_forcefield

# Is peeing (103)
execute as @a[tag=playing,tag=is_peeing] at @s if score @s is_peeing matches 1.. run function ds:items/misc/is_peeing
execute as @a[tag=playing,tag=is_peeing] at @s if score @s is_peeing matches 1.. run scoreboard players remove @s is_peeing 5
execute as @a[tag=playing,tag=is_peeing] at @s if score @s is_peeing matches ..5 run function ds:items/misc/done_peeing

# 100 villagers (110)
execute as @a[tag=playing,tag=100enderman] at @s if score @s 100enderman matches 1.. run function ds:items/misc/100enderman
execute as @a[tag=playing,tag=100enderman] at @s if score @s 100enderman matches 1.. run scoreboard players remove @s 100enderman 1
execute as @a[tag=playing,tag=100enderman] at @s if score @s 100enderman matches ..0 run tag @s remove 100enderman